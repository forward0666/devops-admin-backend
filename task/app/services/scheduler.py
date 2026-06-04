import asyncio
import logging
import time
from datetime import datetime

from app.services.mongodb import get_db
from app.services.executor import execute_task

logger = logging.getLogger(__name__)

scheduler = None
_lock_acquired = False
_lock_task = None

LOCK_KEY = "task:scheduler:lock"
LOCK_TTL = 30  # seconds


async def try_redis_lock(redis_client) -> bool:
    """Try to acquire Redis lock"""
    try:
        result = await redis_client.set(LOCK_KEY, str(int(time.time())), nx=True, ex=LOCK_TTL)
        return result is not None
    except Exception:
        return False


async def refresh_lock(redis_client):
    """Background task: refresh lock TTL every 10 seconds"""
    while True:
        await asyncio.sleep(10)
        try:
            await redis_client.expire(LOCK_KEY, LOCK_TTL)
        except Exception:
            pass


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

    # Remove existing jobs
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
    """Start the scheduler (only one worker acquires Redis lock)"""
    global _lock_acquired, _lock_task

    import redis.asyncio as aioredis
    from app.config import REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_DATABASE

    redis_client = None
    try:
        redis_client = aioredis.Redis(
            host=REDIS_HOST, port=REDIS_PORT,
            password=REDIS_PASSWORD, db=REDIS_DATABASE,
            decode_responses=True,
        )
        # Test connection
        await redis_client.ping()
        logger.info(f"[Scheduler] 🔗 Redis connected: {REDIS_HOST}:{REDIS_PORT}/{REDIS_DATABASE}")
        _lock_acquired = await try_redis_lock(redis_client)
        if not _lock_acquired:
            logger.info("[Scheduler] ⏭️ Another worker holds the lock, skipping")
            await redis_client.close()
            return

        _lock_task = asyncio.create_task(refresh_lock(redis_client))
        logger.info("[Scheduler] ✅ Acquired Redis lock")
    except Exception as e:
        logger.warning(f"[Scheduler] ⚠️ Redis unavailable ({e}), running scheduler without lock")
        _lock_acquired = True
        if redis_client:
            try:
                await redis_client.close()
            except Exception:
                pass

    await load_and_schedule_tasks()
    scheduler.start()
    logger.info("[Scheduler] ✅ Task scheduler started")


async def reload_scheduler():
    """Reload all tasks (call after CRUD operations)"""
    if not _lock_acquired:
        return
    await load_and_schedule_tasks()
    logger.info("[Scheduler] 🔄 Tasks reloaded")


async def stop_scheduler():
    """Stop the scheduler"""
    global scheduler, _lock_task
    if _lock_task:
        _lock_task.cancel()
    if scheduler:
        scheduler.shutdown(wait=False)
        logger.info("[Scheduler] 🛑 Scheduler stopped")
