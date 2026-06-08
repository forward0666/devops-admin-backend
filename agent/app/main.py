import logging
import time

from fastapi import FastAPI, Request
from contextlib import asynccontextmanager

from app.routes import agent
from app.services.db import get_pool, close_pool

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Agent Service starting...")
    try:
        await get_pool()
    except Exception as e:
        logger.error(f"❌ Startup error: {e}")
    yield
    logger.info("🛑 Agent Service shutting down...")
    try:
        await close_pool()
    except Exception as e:
        logger.error(f"❌ Shutdown error: {e}")


app = FastAPI(title="Agent API", version="1.0.0", lifespan=lifespan)


@app.middleware("http")
async def log_requests(request: Request, call_next):
    start = time.monotonic()
    response = await call_next(request)
    elapsed = round((time.monotonic() - start) * 1000, 2)
    logger.info(f"{request.method} {request.url.path} [{response.status_code}] {elapsed}ms")
    return response


app.include_router(agent.router, prefix="/agent", tags=["Agent"])


@app.get("/health")
async def health():
    return {"status": "ok"}
