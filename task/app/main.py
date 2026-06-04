import logging
import asyncio

from fastapi import FastAPI
from contextlib import asynccontextmanager

from app.routes import task
from app.services.mongodb import close_db, get_db
from app.services.scheduler import start_scheduler, stop_scheduler, reload_scheduler

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Task Service starting...")
    await get_db()
    await start_scheduler()
    yield
    logger.info("🛑 Task Service shutting down...")
    await stop_scheduler()
    await close_db()


app = FastAPI(title="Task Service API", version="1.0.0", lifespan=lifespan)

app.include_router(task.router, tags=["Tasks"])


@app.get("/health")
async def health():
    return {"status": "ok"}
