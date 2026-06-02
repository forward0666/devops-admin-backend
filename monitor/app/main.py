import logging
import asyncio

from fastapi import FastAPI
from contextlib import asynccontextmanager

from app.routes import monitor
from app.services.db import close_pool, get_pool
from app.services.mongodb import close_db, get_db
from app.services.nacos_client import send_heartbeat

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)
logging.getLogger("httpx").setLevel(logging.WARNING)


async def heartbeat_loop():
    """Background task: send Nacos heartbeat every 5 seconds"""
    while True:
        await asyncio.sleep(5)
        await send_heartbeat()


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Monitor Service starting...")
    # 预热数据库连接
    await get_pool()
    await get_db()
    task = asyncio.create_task(heartbeat_loop())
    yield
    task.cancel()
    logger.info("🛑 Monitor Service shutting down...")
    await close_pool()
    await close_db()


app = FastAPI(title="Monitor Service API", version="1.0.0", lifespan=lifespan)

app.include_router(monitor.router, prefix="/rules", tags=["Monitor Rules"])


@app.get("/health")
async def health():
    return {"status": "ok"}
