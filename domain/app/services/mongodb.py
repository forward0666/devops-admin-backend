from motor.motor_asyncio import AsyncIOMotorClient
import logging

from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_DATABASE, MONGODB_AUTH_DB

logger = logging.getLogger(__name__)

_client = None
_db = None


async def get_db():
    global _client, _db
    if _db is None:
        uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/{MONGODB_DATABASE}?authSource={MONGODB_AUTH_DB}"
        _client = AsyncIOMotorClient(uri)
        _db = _client[MONGODB_DATABASE]
        logger.info(f"✅ MongoDB connected: {MONGODB_HOST}:{MONGODB_PORT}/{MONGODB_DATABASE}")
    return _db


async def close_db():
    global _client, _db
    if _client:
        _client.close()
        _client = None
        _db = None
