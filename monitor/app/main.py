import logging
import asyncio
import time

from fastapi import FastAPI, Request
from contextlib import asynccontextmanager

from app.routes import monitor
from app.services.db import close_pool, get_pool
from app.services.mongodb import close_db, get_db
from app.config import INTERNAL_WHITELIST_HEADER

logger = logging.getLogger(__name__)
logging.getLogger("httpx").setLevel(logging.WARNING)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Monitor Service starting...")
    await get_pool()
    await get_db()
    yield
    logger.info("🛑 Monitor Service shutting down...")
    await close_pool()
    await close_db()


app = FastAPI(title="Monitor Service API", version="1.0.0", lifespan=lifespan)


@app.middleware("http")
async def log_requests(request: Request, call_next):
    # Log warning for requests without internal call header
    internal_call = request.headers.get(INTERNAL_WHITELIST_HEADER)
    if internal_call != "true":
        logger.warning(f"Request without X-Internal-Call: {request.method} {request.url.path}")

    start = time.monotonic()
    response = await call_next(request)
    elapsed = round((time.monotonic() - start) * 1000, 2)
    client = request.client.host if request.client else "unknown"
    logger.info(f"{request.method} {request.url.path} [{response.status_code}] {elapsed}ms client={client}")
    return response


app.include_router(monitor.router, prefix="/rules", tags=["Monitor Rules"])


@app.get("/health")
async def health():
    return {"status": "ok"}
