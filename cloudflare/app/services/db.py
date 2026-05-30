import aiomysql
import logging
import os
import asyncio

from app.config import MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE

logger = logging.getLogger(__name__)

_pool = None
MAX_RETRIES = 3


async def get_pool():
    global _pool
    if _pool is None:
        _pool = await aiomysql.create_pool(
            host=MYSQL_HOST,
            port=MYSQL_PORT,
            user=MYSQL_USER,
            password=MYSQL_PASSWORD,
            db=MYSQL_DATABASE,
            minsize=2,
            maxsize=10,
            autocommit=True,
            charset='utf8mb4',
            pool_recycle=1800,
        )
        logger.info(f"✅ MySQL pool connected: {MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}")
    return _pool


async def _reset_pool():
    global _pool
    if _pool:
        try:
            _pool.close()
            await _pool.wait_closed()
        except Exception:
            pass
    _pool = None


async def execute(sql: str, args: tuple = None):
    for attempt in range(MAX_RETRIES):
        try:
            pool = await get_pool()
            async with pool.acquire() as conn:
                async with conn.cursor() as cur:
                    await cur.execute(sql, args)
                    return cur.rowcount
        except (aiomysql.Error, OSError, TimeoutError) as e:
            logger.warning(f"⚠️ MySQL execute failed (attempt {attempt+1}/{MAX_RETRIES}): {e}")
            await _reset_pool()
            if attempt < MAX_RETRIES - 1:
                await asyncio.sleep(1)
    raise Exception("MySQL execute failed after retries")


async def query_one(sql: str, args: tuple = None) -> dict:
    for attempt in range(MAX_RETRIES):
        try:
            pool = await get_pool()
            async with pool.acquire() as conn:
                async with conn.cursor(aiomysql.DictCursor) as cur:
                    await cur.execute(sql, args)
                    return await cur.fetchone()
        except (aiomysql.Error, OSError, TimeoutError) as e:
            logger.warning(f"⚠️ MySQL query_one failed (attempt {attempt+1}/{MAX_RETRIES}): {e}")
            await _reset_pool()
            if attempt < MAX_RETRIES - 1:
                await asyncio.sleep(1)
    raise Exception("MySQL query_one failed after retries")


async def query_all(sql: str, args: tuple = None) -> list:
    for attempt in range(MAX_RETRIES):
        try:
            pool = await get_pool()
            async with pool.acquire() as conn:
                async with conn.cursor(aiomysql.DictCursor) as cur:
                    await cur.execute(sql, args)
                    return await cur.fetchall()
        except (aiomysql.Error, OSError, TimeoutError) as e:
            logger.warning(f"⚠️ MySQL query_all failed (attempt {attempt+1}/{MAX_RETRIES}): {e}")
            await _reset_pool()
            if attempt < MAX_RETRIES - 1:
                await asyncio.sleep(1)
    raise Exception("MySQL query_all failed after retries")


async def close_pool():
    global _pool
    if _pool:
        _pool.close()
        await _pool.wait_closed()
        _pool = None
