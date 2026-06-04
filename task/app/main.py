import logging
import asyncio

from fastapi import FastAPI
from contextlib import asynccontextmanager

from app.routes import task
from app.services.db import get_pool, init_db, close_pool
from app.services.scheduler import start_scheduler, stop_scheduler

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Task Service starting...")
    try:
        await get_pool()
        await init_db()
        await start_scheduler()
    except Exception as e:
        logger.error(f"❌ Startup error (will continue): {e}")
    yield
    logger.info("🛑 Task Service shutting down...")
    try:
        await stop_scheduler()
        await close_pool()
    except Exception as e:
        logger.error(f"❌ Shutdown error: {e}")


app = FastAPI(title="Task Service API", version="1.0.0", lifespan=lifespan)

app.include_router(task.router, tags=["Tasks"])


@app.get("/health")
async def health():
    return {"status": "ok"}
