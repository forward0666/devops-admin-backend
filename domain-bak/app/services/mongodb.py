from motor.motor_asyncio import AsyncIOMotorClient
import logging

from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_DATABASE, MONGODB_AUTH_DB, CLOUDFLARE_MONGODB_DATABASE

logger = logging.getLogger(__name__)

_client = None
_db = None
_source_db = None


async def get_db():
    global _client, _db
    if _db is None:
        uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/{MONGODB_DATABASE}?authSource={MONGODB_AUTH_DB}"
        _client = AsyncIOMotorClient(uri)
        _db = _client[MONGODB_DATABASE]
        logger.info(f"✅ MongoDB connected: {MONGODB_HOST}:{MONGODB_PORT}/{MONGODB_DATABASE}")
    return _db


async def get_source_db():
    """获取源数据库（cloudflare 库）连接"""
    global _client, _source_db
    if _source_db is None:
        if _client is None:
            await get_db()
        _source_db = _client[CLOUDFLARE_MONGODB_DATABASE]
        logger.info(f"✅ Source MongoDB: {CLOUDFLARE_MONGODB_DATABASE}")
    return _source_db


async def close_db():
    global _client, _db, _source_db
    if _client:
        _client.close()
        _client = None
        _db = None
        _source_db = None
