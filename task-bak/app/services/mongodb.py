from motor.motor_asyncio import AsyncIOMotorClient
import logging

from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_DATABASE, MONGODB_AUTH_DB

logger = logging.getLogger(__name__)

_client = None
_db = None


async def get_db():
    """Get MongoDB database instance (lazy init)"""
    global _client, _db
    if _db is None:
        uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/{MONGODB_AUTH_DB}"
        _client = AsyncIOMotorClient(uri, serverSelectionTimeoutMS=5000)
        _db = _client[MONGODB_DATABASE]
        logger.info(f"📦 Connected to MongoDB: {MONGODB_HOST}:{MONGODB_PORT}/{MONGODB_DATABASE}")
    return _db


async def close_db():
    """Close MongoDB connection"""
    global _client, _db
    if _client:
        _client.close()
        _client = None
        _db = None
