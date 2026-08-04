import logging
from motor.motor_asyncio import AsyncIOMotorClient
from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_DATABASE, MONGODB_AUTH_DB

logger = logging.getLogger(__name__)

_client: AsyncIOMotorClient = None


async def get_db():
    global _client
    if _client is None:
        uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/{MONGODB_DATABASE}?authSource={MONGODB_AUTH_DB}"
        _client = AsyncIOMotorClient(uri)
        logger.info(f"✅ MongoDB connected: {MONGODB_HOST}:{MONGODB_PORT}/{MONGODB_DATABASE}")
    return _client[MONGODB_DATABASE]


async def close_db():
    global _client
    if _client:
        _client.close()
        _client = None
        logger.info("🛑 MongoDB connection closed")
