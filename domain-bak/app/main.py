import logging
import time

from fastapi import FastAPI, Request
from contextlib import asynccontextmanager

from app.routes import domain, dns_domain, sync_domain, statistic
from app.services.mongodb import close_db, get_db
from app.services.db import get_pool, close_pool
from app.services.redis import get_redis, close_redis
from app.config import INTERNAL_WHITELIST_HEADER

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)
logging.getLogger("httpx").setLevel(logging.WARNING)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Domain Manager starting...")
    await get_db()
    await get_pool()
    await get_redis()
    yield
    logger.info("🛑 Domain Manager shutting down...")
    await close_redis()
    await close_pool()
    await close_db()


app = FastAPI(title="Domain Manager API", version="1.0.0", lifespan=lifespan)


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
    logger.info(f"{request.method} {request.url.path}?{request.query_params} [{response.status_code}] {elapsed}ms client={client}")
    return response


app.include_router(domain.router, tags=["Domain"])
app.include_router(dns_domain.router, prefix="/domain", tags=["DNS Domain"])
app.include_router(sync_domain.router, prefix="/sync_domain", tags=["Sync Domain"])
app.include_router(statistic.router, prefix="/statistic", tags=["Statistic"])


@app.get("/health")
async def health():
    return {"status": "ok"}
