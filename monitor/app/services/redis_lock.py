import logging
import time
import aioredis
from app.config import REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_DATABASE

logger = logging.getLogger(__name__)

_redis: aioredis.Redis = None

LOCK_KEY_PREFIX = "monitor:lock:"
LOCK_TTL = 30  # 30 seconds, extended by extend_lock during task


async def get_redis() -> aioredis.Redis:
    global _redis
    if _redis is None:
        _redis = aioredis.from_url(
            f"redis://:{REDIS_PASSWORD}@{REDIS_HOST}:{REDIS_PORT}/{REDIS_DATABASE}",
            decode_responses=True,
        )
        logger.info(f"✅ Redis connected: {REDIS_HOST}:{REDIS_PORT}")
    return _redis


async def close_redis():
    global _redis
    if _redis:
        await _redis.close()
        _redis = None
        logger.info("🛑 Redis connection closed")


async def acquire_lock(task_name: str) -> bool:
    """Try to acquire a distributed lock. Returns True if acquired."""
    redis = await get_redis()
    key = f"{LOCK_KEY_PREFIX}{task_name}"
    try:
        result = await redis.set(key, "1", nx=True, ex=LOCK_TTL)
        if result:
            logger.info(f"[Lock] Acquired lock for '{task_name}'")
            return True
        else:
            logger.info(f"[Lock] Task '{task_name}' is already running on another pod")
            return False
    except Exception as e:
        logger.error(f"[Lock] Failed to acquire lock: {e}")
        return False


async def release_lock(task_name: str):
    """Release the distributed lock."""
    redis = await get_redis()
    key = f"{LOCK_KEY_PREFIX}{task_name}"
    try:
        await redis.delete(key)
        logger.info(f"[Lock] Released lock for '{task_name}'")
    except Exception as e:
        logger.error(f"[Lock] Failed to release lock: {e}")


async def extend_lock(task_name: str):
    """Extend the lock TTL (call periodically during long tasks)."""
    redis = await get_redis()
    key = f"{LOCK_KEY_PREFIX}{task_name}"
    try:
        await redis.expire(key, LOCK_TTL)
    except Exception as e:
        logger.error(f"[Lock] Failed to extend lock: {e}")
