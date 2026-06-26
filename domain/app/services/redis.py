import redis.asyncio as aioredis
import logging

from app.config import REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_DATABASE

logger = logging.getLogger(__name__)

_redis = None


async def get_redis():
    global _redis
    if _redis is None:
        _redis = aioredis.Redis(
            host=REDIS_HOST,
            port=REDIS_PORT,
            password=REDIS_PASSWORD or None,
            db=REDIS_DATABASE,
            decode_responses=True,
        )
        logger.info(f"✅ Redis connected: {REDIS_HOST}:{REDIS_PORT}")
    return _redis


async def close_redis():
    global _redis
    if _redis:
        await _redis.close()
        _redis = None
