import logging
import asyncio

from fastapi import FastAPI
from contextlib import asynccontextmanager

from app.routes import accounts, zones, dns, security, ssl, cache, cache_rule, security_rules, whitelist
from app.services.db import close_pool, get_pool
from app.services.redis import close_redis
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
    logger.info("🚀 Cloudflare Manager starting...")
    # 预热数据库连接
    await get_pool()
    await get_db()
    task = asyncio.create_task(heartbeat_loop())
    yield
    task.cancel()
    logger.info("🛑 Cloudflare Manager shutting down...")
    await close_pool()
    await close_redis()
    await close_db()


app = FastAPI(title="Cloudflare Manager API", version="1.0.0", lifespan=lifespan)

app.include_router(accounts.router, prefix="/accounts", tags=["Accounts"])
app.include_router(zones.router, prefix="/zones", tags=["Zones"])
app.include_router(security.router, prefix="/security", tags=["Security"])
app.include_router(security.zone_router, prefix="/zones/{zone_id}/security", tags=["Security"])
app.include_router(ssl.router, prefix="/ssl", tags=["SSL"])
app.include_router(ssl.zone_router, prefix="/zones/{zone_id}/ssl", tags=["SSL"])
app.include_router(cache.router, prefix="/cache", tags=["Cache"])
app.include_router(cache.zone_router, prefix="/zones/{zone_id}/cache", tags=["Cache"])
app.include_router(dns.router, prefix="/dns", tags=["DNS"])
app.include_router(cache_rule.router, prefix="/cacheRule", tags=["CacheRule"])
app.include_router(security_rules.router, prefix="/securityRules", tags=["SecurityRules"])
app.include_router(whitelist.router, prefix="/whitelist", tags=["Whitelist"])



@app.get("/health")
async def health():
    return {"status": "ok"}
