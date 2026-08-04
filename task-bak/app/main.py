import logging
import asyncio
import time

from fastapi import FastAPI, Request

import sys
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s",
                    handlers=[logging.StreamHandler(sys.stdout)])
for handler in logging.root.handlers:
    handler.stream.reconfigure(encoding='utf-8')
logger = logging.getLogger(__name__)
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)
logging.getLogger("anyio").setLevel(logging.WARNING)
from contextlib import asynccontextmanager

from app.routes import task
from app.services.db import get_pool, close_pool
from app.services.scheduler import start_scheduler, stop_scheduler
from app.config import INTERNAL_WHITELIST_HEADER


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Task Service starting...")
    try:
        await get_pool()
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


@app.middleware("http")
async def log_requests(request: Request, call_next):
    # Skip auth for internal service calls
    internal_call = request.headers.get(INTERNAL_WHITELIST_HEADER)
    if internal_call != "true":
        # External request without internal header - reject or check auth
        # For now, just log and pass through (gateway adds the header)
        logger.warning(f"Request without X-Internal-Call: {request.method} {request.url.path}")

    start = time.monotonic()
    response = await call_next(request)
    elapsed = round((time.monotonic() - start) * 1000, 2)
    client = request.client.host if request.client else "unknown"
    logger.info(f"{request.method} {request.url.path} [{response.status_code}] {elapsed}ms client={client}")
    return response


app.include_router(task.router, tags=["Tasks"])


@app.get("/health")
async def health():
    return {"status": "ok"}
