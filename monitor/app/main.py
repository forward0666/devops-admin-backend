import logging
import asyncio

from fastapi import FastAPI
from contextlib import asynccontextmanager

from app.routes import monitor
from app.services.db import close_pool, get_pool
from app.services.mongodb import close_db, get_db
from app.services.checker import scheduler_loop

logger = logging.getLogger(__name__)
logging.getLogger("httpx").setLevel(logging.WARNING)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Monitor Service starting...")
    await get_pool()
    await get_db()
    scheduler_task = asyncio.create_task(scheduler_loop())
    yield
    scheduler_task.cancel()
    logger.info("🛑 Monitor Service shutting down...")
    await close_pool()
    await close_db()
    # await close_redis()


app = FastAPI(title="Monitor Service API", version="1.0.0", lifespan=lifespan)

app.include_router(monitor.router, prefix="/rules", tags=["Monitor Rules"])


@app.get("/health")
async def health():
    return {"status": "ok"}
