import logging
import time

from fastapi import FastAPI, Request
from contextlib import asynccontextmanager

from app.routes import domain, dns_domain
from app.services.mongodb import close_db, get_db

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)
logging.getLogger("httpx").setLevel(logging.WARNING)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Domain Manager starting...")
    await get_db()
    yield
    logger.info("🛑 Domain Manager shutting down...")
    await close_db()


app = FastAPI(title="Domain Manager API", version="1.0.0", lifespan=lifespan)


@app.middleware("http")
async def log_requests(request: Request, call_next):
    start = time.monotonic()
    response = await call_next(request)
    elapsed = round((time.monotonic() - start) * 1000, 2)
    client = request.client.host if request.client else "unknown"
    logger.info(f"{request.method} {request.url.path}?{request.query_params} [{response.status_code}] {elapsed}ms client={client}")
    return response


app.include_router(domain.router, tags=["Domain"])
app.include_router(dns_domain.router, prefix="/dnsDomain", tags=["DNS Domain"])


@app.get("/health")
async def health():
    return {"status": "ok"}
