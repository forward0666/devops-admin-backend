import logging
import asyncio
import time

from fastapi import FastAPI, Request
from contextlib import asynccontextmanager

from app.routes import accounts, zones, dns, security, ssl, cache, cache_rule, ratelimit, sync_rules, ddos, managed, lists, statistic
from app.services.db import close_pool, get_pool
from app.services.redis import close_redis
from app.services.mongodb import close_db, get_db
from app.config import INTERNAL_WHITELIST_HEADER

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)
logging.getLogger("httpx").setLevel(logging.WARNING)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 Cloudflare Manager starting...")
    await get_pool()
    await get_db()
    yield
    logger.info("🛑 Cloudflare Manager shutting down...")
    await close_pool()
    await close_redis()
    await close_db()


app = FastAPI(title="Cloudflare Manager API", version="1.0.0", lifespan=lifespan)


@app.middleware("http")
async def log_requests(request: Request, call_next):
    # Log warning for requests without internal call header
    internal_call = request.headers.get(INTERNAL_WHITELIST_HEADER)
    if internal_call != "true":
        logger.warning(f"Request without X-Internal-Call: {request.method} {request.url.path}")

    start = time.monotonic()
    # Read body for POST/PUT requests
    body_str = ""
    if request.method in ("POST", "PUT"):
        try:
            body = await request.body()
            if body:
                import json
                body_str = f" body={json.loads(body)}"
        except Exception:
            pass
    response = await call_next(request)
    elapsed = round((time.monotonic() - start) * 1000, 2)
    client = request.client.host if request.client else "unknown"
    logger.info(f"{request.method} {request.url.path}?{request.query_params}{body_str} [{response.status_code}] {elapsed}ms client={client}")
    return response


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


app.include_router(ratelimit.router, prefix="/ratelimit", tags=["RateLimit"])
app.include_router(ratelimit.zone_router, prefix="/zones/{zone_id}/ratelimit", tags=["RateLimit"])
app.include_router(sync_rules.router, prefix="/syncRules", tags=["SyncRules"])
app.include_router(ddos.router, prefix="/ddos", tags=["DDoS"])
app.include_router(ddos.zone_router, prefix="/zones/{zone_id}/ddos", tags=["DDoS"])
app.include_router(managed.router, prefix="/managed", tags=["Managed"])
app.include_router(managed.zone_router, prefix="/zones/{zone_id}/managed", tags=["Managed"])
app.include_router(lists.router, prefix="/configurations/lists", tags=["Lists"])
app.include_router(statistic.router, prefix="/statistic", tags=["Statistic"])



@app.get("/health")
async def health():
    return {"status": "ok"}
