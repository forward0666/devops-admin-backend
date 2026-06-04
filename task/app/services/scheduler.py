import asyncio
import logging
import os
import time
import uuid
from datetime import datetime

from app.services.mongodb import get_db
from app.services.executor import execute_task

logger = logging.getLogger(__name__)

scheduler = None
_lock_acquired = False
_redis_lock_task = None
_lock_id = str(uuid.uuid4())  # Unique per pod instance

REDIS_LOCK_KEY = "task:scheduler:lock"
REDIS_LOCK_TTL = 30
RETRY_INTERVAL = 5  # seconds


async def try_redis_lock(redis_client) -> bool:
    """Try to acquire Redis lock with unique pod ID"""
    try:
        result = await redis_client.set(
            REDIS_LOCK_KEY, _lock_id, nx=True, ex=REDIS_LOCK_TTL
        )
        return result is not None
    except Exception:
        return False


async def release_redis_lock(redis_client):
    """Release Redis lock only if we own it (Lua script for atomicity)"""
    lua_script = """
    if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("del", KEYS[1])
    else
        return 0
    end
    """
    try:
        await redis_client.eval(lua_script, 1, REDIS_LOCK_KEY, _lock_id)
    except Exception:
        pass


async def refresh_redis_lock(redis_client):
    """Refresh Redis lock TTL periodically"""
    while True:
        await asyncio.sleep(10)
        try:
            current = await redis_client.get(REDIS_LOCK_KEY)
            if current == _lock_id:
                await redis_client.expire(REDIS_LOCK_KEY, REDIS_LOCK_TTL)
            else:
                logger.warning("[Scheduler] ⚠️ Lost Redis lock!")
                break
        except Exception:
            pass


async def retry_redis_lock(redis_client):
    """Keep trying to acquire lock, start scheduler when successful"""
    global _lock_acquired
    while True:
        await asyncio.sleep(RETRY_INTERVAL)
        try:
            _lock_acquired = await try_redis_lock(redis_client)
            if _lock_acquired:
                logger.info(f"[Scheduler] 🔒 Acquired Redis lock (id={_lock_id[:8]})")
                asyncio.create_task(refresh_redis_lock(redis_client))
                await load_and_schedule_tasks()
                scheduler.start()
                logger.info("[Scheduler] ✅ Task scheduler started")
                return
        except Exception as e:
            logger.warning(f"[Scheduler] ⚠️ Retry lock error: {e}")


async def load_and_schedule_tasks():
    """Load all enabled tasks from MongoDB and schedule them"""
    global scheduler
    from apscheduler.schedulers.asyncio import AsyncIOScheduler
    from apscheduler.triggers.cron import CronTrigger

    if scheduler is None:
        scheduler = AsyncIOScheduler()

    db = await get_db()
    tasks = []
    async for doc in db.tasks.find({"enabled": True}):
        doc["_id"] = str(doc["_id"])
        tasks.append(doc)

    for job in scheduler.get_jobs():
        if job.id.startswith("task_"):
            scheduler.remove_job(job.id)

    for task in tasks:
        task_id = task["_id"]
        cron_expr = task.get("cron", "")
        if not cron_expr:
            continue

        try:
            trigger = CronTrigger.from_crontab(cron_expr)
            scheduler.add_job(
                run_task_wrapper,
                trigger=trigger,
                id=f"task_{task_id}",
                args=[task],
                replace_existing=True,
            )
            logger.info(f"[Scheduler] Scheduled: {task['name']} ({cron_expr})")
        except Exception as e:
            logger.error(f"[Scheduler] Failed to schedule {task['name']}: {e}")


async def run_task_wrapper(task: dict):
    """Wrapper for task execution with logging"""
    task_name = task.get("name", "unknown")
    task_id = task.get("_id")
    logger.info(f"[Scheduler] ▶️ Running: {task_name}")
    try:
        await execute_task(task)
        logger.info(f"[Scheduler] ✅ Done: {task_name}")
    except Exception as e:
        logger.error(f"[Scheduler] ❌ Failed: {task_name}: {e}")
        try:
            from bson import ObjectId
            db = await get_db()
            await db.tasks.update_one(
                {"_id": ObjectId(task_id)},
                {"$set": {"last_status": "failed"}}
            )
        except Exception:
            pass


async def start_scheduler():
    """Start the scheduler with Redis lock + retry"""
    global _lock_acquired, _redis_lock_task

    try:
        import redis.asyncio as aioredis
        from app.config import REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_DATABASE

        redis_client = aioredis.Redis(
            host=REDIS_HOST, port=REDIS_PORT,
            password=REDIS_PASSWORD, db=REDIS_DATABASE,
            decode_responses=True,
        )
        await redis_client.ping()
        logger.info(f"[Scheduler] 🔗 Redis connected: {REDIS_HOST}:{REDIS_PORT}/{REDIS_DATABASE}")

        _lock_acquired = await try_redis_lock(redis_client)
        if _lock_acquired:
            logger.info(f"[Scheduler] 🔒 Acquired Redis lock (id={_lock_id[:8]})")
            _redis_lock_task = asyncio.create_task(refresh_redis_lock(redis_client))
            await load_and_schedule_tasks()
            scheduler.start()
            logger.info("[Scheduler] ✅ Task scheduler started")
        else:
            logger.info("[Scheduler] ⏭️ Another pod holds the lock, will retry every 5s")
            _redis_lock_task = asyncio.create_task(retry_redis_lock(redis_client))
    except Exception as e:
        logger.error(f"[Scheduler] ❌ Redis error: {e}, scheduler not started")


async def reload_scheduler():
    """Reload all tasks (call after CRUD operations)"""
    if not _lock_acquired:
        return
    await load_and_schedule_tasks()
    logger.info("[Scheduler] 🔄 Tasks reloaded")


async def stop_scheduler():
    """Stop the scheduler and release lock"""
    global scheduler, _redis_lock_task
    if _redis_lock_task:
        _redis_lock_task.cancel()
    if scheduler:
        scheduler.shutdown(wait=False)
        logger.info("[Scheduler] 🛑 Scheduler stopped")
    try:
        import redis.asyncio as aioredis
        from app.config import REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_DATABASE
        redis_client = aioredis.Redis(
            host=REDIS_HOST, port=REDIS_PORT,
            password=REDIS_PASSWORD, db=REDIS_DATABASE,
            decode_responses=True,
        )
        await release_redis_lock(redis_client)
        await redis_client.close()
        logger.info("[Scheduler] 🔓 Released Redis lock")
    except Exception:
        pass
