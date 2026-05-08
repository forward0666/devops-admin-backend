import logging

from fastapi import FastAPI
from contextlib import asynccontextmanager

from app.routes import accounts, zones, dns, firewall, ssl, cache
from app.services.db import close_pool
from app.services.redis import close_redis
from app.services.mongodb import close_db

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Cloudflare Manager starting...")
    yield
    logger.info("🛑 Cloudflare Manager shutting down...")
    await close_pool()
    await close_redis()
    await close_db()


app = FastAPI(title="Cloudflare Manager API", version="1.0.0", lifespan=lifespan)

app.include_router(accounts.router, prefix="/accounts", tags=["Accounts"])
app.include_router(zones.router, prefix="/zones", tags=["Zones"])
app.include_router(dns.router, prefix="/zones/{zone_id}/dns", tags=["DNS"])
app.include_router(firewall.router, prefix="/zones/{zone_id}/firewall", tags=["Firewall"])
app.include_router(ssl.router, prefix="/zones/{zone_id}/ssl", tags=["SSL"])
app.include_router(cache.router, prefix="/zones/{zone_id}/cache", tags=["Cache"])


@app.get("/health")
async def health():
    return {"status": "ok"}
