import logging
import aiomysql
from app.config import MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE, MYSQL_POOL_RECYCLE

logger = logging.getLogger(__name__)

_pool: aiomysql.Pool = None


async def get_pool():
    global _pool
    if _pool is None:
        _pool = await aiomysql.create_pool(
            host=MYSQL_HOST,
            port=MYSQL_PORT,
            user=MYSQL_USER,
            password=MYSQL_PASSWORD,
            db=MYSQL_DATABASE,
            charset="utf8mb4",
            autocommit=True,
            minsize=1,
            maxsize=10,
            pool_recycle=MYSQL_POOL_RECYCLE,
        )
        logger.info(f"✅ MySQL connected: {MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}")
    return _pool


async def close_pool():
    global _pool
    if _pool:
        _pool.close()
        await _pool.wait_closed()
        _pool = None
        logger.info("🛑 MySQL pool closed")


async def init_db():
    """Create task table if not exists"""
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute("""
                CREATE TABLE IF NOT EXISTS task (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    cron VARCHAR(100) NOT NULL,
                    enabled TINYINT(1) NOT NULL DEFAULT 1,
                    description TEXT,
                    config JSON,
                    last_run_at DATETIME NULL,
                    last_status VARCHAR(20) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
            logger.info("✅ Task table ready")


async def query_all(sql: str, args=None):
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            await cur.execute(sql, args)
            return await cur.fetchall()


async def query_one(sql: str, args=None):
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            await cur.execute(sql, args)
            return await cur.fetchone()


async def execute(sql: str, args=None):
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(sql, args)
            return cur.rowcount
