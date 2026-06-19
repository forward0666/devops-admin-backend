import logging
import asyncio
import httpx
import os
import time
from datetime import datetime

from app.services.db import query_all, execute
from app.services.mongodb import get_db

logger = logging.getLogger(__name__)

# ─── Config ──────────────────────────────────────────────
CHECK_CONCURRENCY = int(os.getenv("HTTP_CONCURRENCY", "50"))  # HTTP concurrency limit
DNS_CONCURRENCY = int(os.getenv("DNS_CONCURRENCY", "50"))   # DNS concurrency limit
HTTP_POOL_SIZE = int(os.getenv("HTTP_POOL_SIZE", "50"))     # httpx connection pool size
logger.info(f"[Config] HTTP_CONCURRENCY={CHECK_CONCURRENCY}, DNS_CONCURRENCY={DNS_CONCURRENCY}, HTTP_POOL_SIZE={HTTP_POOL_SIZE}")
TIMEOUT = 3.0
DNS_CACHE_TTL = 600


# ─── aiodns Resolver ────────────────────────────────────
_dns_resolver = None


def _get_dns_resolver():
    global _dns_resolver
    if _dns_resolver is None:
        import aiodns
        _dns_resolver = aiodns.DNSResolver(
            nameservers=["8.8.8.8", "8.8.4.4"],
            timeout=CONNECT_TIMEOUT,
        )
    return _dns_resolver


# ─── Redis DNS Cache ────────────────────────────────────
_redis = None


async def _get_redis():
    global _redis
    if _redis is None:
        import redis.asyncio as aioredis
        from app.config import REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_DATABASE
        _redis = aioredis.Redis(
            host=REDIS_HOST, port=REDIS_PORT,
            password=REDIS_PASSWORD, db=REDIS_DATABASE,
            decode_responses=True,
        )
    return _redis


async def resolve_domain(domain: str) -> tuple[str | None, str]:
    """Resolve domain: Redis cache -> aiodns -> system. Returns (ip, record_type)"""
    # 1. Try Redis cache
    try:
        r = await _get_redis()
        cached = await r.get(f"dns:{domain}")
        if cached:
            # Check if it's a CNAME or A record
            record_type = "CNAME" if "." in cached and not cached.replace(".", "").isdigit() else "A"
            return cached, record_type
    except Exception:
        pass

    # 2. Try aiodns
    try:
        resolver = _get_dns_resolver()
        result = await resolver.query(domain, "A")
        if result.addresses:
            ip = result.addresses[0]
            # Write back to Redis
            try:
                await r.setex(f"dns:{domain}", DNS_CACHE_TTL, ip)
            except Exception:
                pass
            return ip, "A"
    except Exception:
        pass

    # 3. Fallback to system DNS
    try:
        loop = asyncio.get_event_loop()
        infos = await loop.getaddrinfo(domain, None, family=socket.AF_INET)
        if infos:
            ip = infos[0][4][0]
            try:
                await r.setex(f"dns:{domain}", DNS_CACHE_TTL, ip)
            except Exception:
                pass
            return ip, "A"
    except Exception:
        pass

    return None, "unknown"


# ─── Probe IP ────────────────────────────────────────────
_probe_ip: str = None


def get_probe_ip() -> str:
    global _probe_ip
    if _probe_ip:
        return _probe_ip
    try:
        resp = httpx.get('https://ipinfo.io/json', timeout=10, verify=False)
        _probe_ip = resp.json().get('ip', 'fail')
        return _probe_ip
    except Exception as e:
        logger.warning(f"Failed to get probe IP: {e}")
        return "fail"


async def async_get_probe_ip() -> str:
    return await asyncio.to_thread(get_probe_ip)


# ─── Concurrency Semaphores ─────────────────────────────
_http_sem = None
_dns_sem = None


def _get_http_sem():
    global _http_sem
    if _http_sem is None:
        _http_sem = asyncio.Semaphore(CHECK_CONCURRENCY)
    return _http_sem


def _get_dns_sem():
    global _dns_sem
    if _dns_sem is None:
        _dns_sem = asyncio.Semaphore(DNS_CONCURRENCY)
    return _dns_sem
_client_http: httpx.AsyncClient = None
_client_https: httpx.AsyncClient = None


def _make_timeout() -> httpx.Timeout:
    return httpx.Timeout(timeout=TIMEOUT)


def _make_limits() -> httpx.Limits:
    return httpx.Limits(
        max_connections=HTTP_POOL_SIZE,
        max_keepalive_connections=HTTP_POOL_SIZE,
    )


def _get_client(https: bool = False) -> httpx.AsyncClient:
    global _client_http, _client_https
    if https:
        if _client_https is None or _client_https.is_closed:
            _client_https = httpx.AsyncClient(
                timeout=_make_timeout(),
                follow_redirects=False,
                limits=_make_limits(),
                verify=False,
            )
        return _client_https
    else:
        if _client_http is None or _client_http.is_closed:
            _client_http = httpx.AsyncClient(
                timeout=_make_timeout(),
                follow_redirects=False,
                limits=_make_limits(),
            )
        return _client_http


def _close_clients():
    global _client_http, _client_https
    for c in (_client_http, _client_https):
        if c and not c.is_closed:
            asyncio.get_event_loop().create_task(c.aclose())
    _client_http = None
    _client_https = None


# ─── Check Single Domain ────────────────────────────────
import socket


async def _probe(scheme: str, domain: str) -> tuple[httpx.Response | None, float, str]:
    """Send HEAD, fallback to GET on 405/403/444"""
    start = time.monotonic()
    client = _get_client(https=(scheme == "https"))
    url = f"{scheme}://{domain}"

    try:
        resp = await client.head(url)
        elapsed = (time.monotonic() - start) * 1000
        # HEAD 被拒绝，降级 GET（403 不降级，已算 up）
        if resp.status_code in (405, 444):
            try:
                resp = await client.get(url)
                elapsed = (time.monotonic() - start) * 1000
            except Exception:
                pass
        return resp, elapsed, scheme
    except (httpx.ConnectError, httpx.ConnectTimeout, httpx.TimeoutException):
        return None, (time.monotonic() - start) * 1000, scheme
    except Exception:
        return None, (time.monotonic() - start) * 1000, scheme


async def check_domain(domain: str, last_protocol: str | None = None) -> dict:
    """Check domain with HTTP/HTTPS concurrent race"""
    result = {
        "domain": domain,
        "status": "timeout",
        "status_code": None,
        "response_time_ms": None,
        "resolved_ip": None,
        "probe_ip": None,
        "error": None,
        "checked_at": datetime.utcnow(),
    }

    # DNS resolve (with concurrency limit)
    dns_start = time.monotonic()
    result["resolved_ip"], result["record_type"] = await resolve_domain(domain)
    dns_ms = round((time.monotonic() - dns_start) * 1000, 2)

    # Determine protocol order
    if last_protocol == "https":
        schemes = ("https", "http")
    else:
        schemes = ("http",)

    # Single request - any response = up, only timeout/DNS failure = error
    start = time.monotonic()
    probes = [_probe(s, domain) for s in schemes]

    try:
        for coro in asyncio.as_completed(probes):
            resp, probe_elapsed, protocol = await coro
            if resp is not None:
                elapsed = (time.monotonic() - start) * 1000
                result["status_code"] = resp.status_code
                result["response_time_ms"] = round(elapsed, 2)
                        # Any response = up, only timeout/DNS failure = error
                result["status"] = "up"
                result["_protocol"] = protocol
                result["_dns_ms"] = dns_ms
                return result

        # Both failed
        elapsed = (time.monotonic() - start) * 1000
        result["response_time_ms"] = round(elapsed, 2)
        result["error"] = "Connection failed"
        result["_dns_ms"] = dns_ms

    except Exception as e:
        elapsed = (time.monotonic() - start) * 1000
        result["response_time_ms"] = round(elapsed, 2)
        result["error"] = str(e)[:200]
        result["_dns_ms"] = dns_ms

    return result


# ─── Run Check For Rule ─────────────────────────────────
async def run_check_for_rule(rule: dict):
    """Run check for a single rule and save results to MongoDB"""
    rule_id = rule["id"]
    rule_name = rule["name"]
    domains = rule.get("domains", [])
    if isinstance(domains, str):
        import json
        try: domains = json.loads(domains)
        except: domains = []

    # ── Step 1: Load domains ──
    domains_to_check = []
    try:
        from motor.motor_asyncio import AsyncIOMotorClient
        from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_AUTH_DB, DOMAIN_MONGODB_DATABASE
        uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/{DOMAIN_MONGODB_DATABASE}?authSource={MONGODB_AUTH_DB}"
        domain_client_mongo = AsyncIOMotorClient(uri)
        domain_db = domain_client_mongo[DOMAIN_MONGODB_DATABASE]
        if domains:
            # Check selected domains only
            domains_to_check = [d for d in domains if d]
        else:
            # Check all non-ignored domains
            docs = await domain_db["domain"].find({"is_ignored": {"$ne": True}}, {"name": 1}).to_list(length=10000)
            for doc in docs:
                name = doc.get("name", "")
                if name and name not in domains_to_check:
                    domains_to_check.append(name)
        domain_client_mongo.close()
    except Exception as e:
        logger.error(f"[Monitor] Failed to fetch domains: {e}")

    if not domains_to_check:
        logger.warning(f"[Monitor] Rule '{rule_name}' has no domains to check")
        return

    num_domains = len(domains_to_check)
    logger.info(f"[Step 1] Loaded {num_domains} domains, HTTP concurrency={CHECK_CONCURRENCY}, DNS concurrency={DNS_CONCURRENCY}")

    # ── Step 2: Init ──
    await execute("UPDATE monitor_rule SET status='running', updated_at=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
    logger.info(f"[Step 2] Rule status set to running")

    db = await get_db()
    collection = db[f"monitor_results_{rule_id}"]
    await collection.create_index([("rule_id", 1), ("domain", 1)], unique=True, background=True)
    now = datetime.utcnow()

    probe_ip = await async_get_probe_ip()
    logger.info(f"[Step 3] Probe IP: {probe_ip}")

    # ── Step 4: Load previous results and sort domains ──
    last_protocols: dict[str, str] = {}
    last_status: dict[str, str] = {}
    last_record_type: dict[str, str] = {}
    try:
        prev_results = await collection.find(
            {"rule_id": rule_id},
            {"domain": 1, "last_protocol": 1, "status": 1, "record_type": 1},
        ).to_list(length=10000)
        for r in prev_results:
            d = r.get("domain")
            if d:
                if r.get("last_protocol"):
                    last_protocols[d] = r["last_protocol"]
                if r.get("status"):
                    last_status[d] = r["status"]
                if r.get("record_type"):
                    last_record_type[d] = r["record_type"]
        logger.info(f"[Step 4] Loaded {len(last_protocols)} protocol hints, {len(last_status)} status hints")
    except Exception:
        pass

    # Sort: A good > CNAME all > A bad
    def _sort_key(domain: str):
        rt = last_record_type.get(domain, "")
        status = last_status.get(domain, "")
        is_good = status in ("up", "3xx", "4xx", "5xx")

        if rt == "A" and is_good:
            group = 0
            status_order = {"up": 0, "3xx": 1, "4xx": 2, "5xx": 3}[status]
        elif rt == "CNAME":
            group = 1
            status_order = {"up": 0, "3xx": 1, "4xx": 2, "5xx": 3, "timeout": 4}.get(status, 5)
        elif rt == "A" and not is_good:
            group = 2
            status_order = 4 if status == "timeout" else 5
        else:
            group = 3
            status_order = 0
        return (group, status_order)

    domains_to_check.sort(key=_sort_key)
    logger.info(f"[Step 4] Sorted: A good > CNAME all > A bad")

    # ── Step 5: Connect Domain DB ──
    domain_client_mongo = None
    dns_col = None
    try:
        from motor.motor_asyncio import AsyncIOMotorClient
        from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_AUTH_DB, DOMAIN_MONGODB_DATABASE
        uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/{DOMAIN_MONGODB_DATABASE}?authSource={MONGODB_AUTH_DB}"
        domain_client_mongo = AsyncIOMotorClient(uri)
        dns_col = domain_client_mongo[DOMAIN_MONGODB_DATABASE]["domain"]
    except Exception as e:
        logger.error(f"[Step 5] Failed to connect domain DB: {e}")

    # ── Step 5.5: Pre-resolve DNS (batch, higher concurrency) ──
    dns_pre_sem = asyncio.Semaphore(DNS_CONCURRENCY * 2)
    async def _pre_resolve(domain: str):
        async with dns_pre_sem:
            await resolve_domain(domain)
    dns_start = time.monotonic()
    await asyncio.gather(*[_pre_resolve(d) for d in domains_to_check], return_exceptions=True)
    dns_elapsed = round(time.monotonic() - dns_start, 2)
    logger.info(f"[Step 5.5] DNS pre-resolved {len(domains_to_check)} domains in {dns_elapsed}s")

    # ── Step 6: HTTP checks with semaphore (streaming) ──
    sem = asyncio.Semaphore(CHECK_CONCURRENCY)

    async def checked(domain: str) -> dict:
        async with sem:
            return await check_domain(domain, last_protocols.get(domain))

    logger.info(f"[Step 6] Starting HTTP checks, concurrency={CHECK_CONCURRENCY}")
    check_start = time.monotonic()

    tasks = [checked(d) for d in domains_to_check]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    check_elapsed = round(time.monotonic() - check_start, 2)
    logger.info(f"[Step 6] HTTP checks completed in {check_elapsed}s")

    # ── Step 7: Process results ──
    up_count = 0
    down_count = 0
    error_count = 0
    up_times: list[float] = []
    dns_times: list[float] = []
    protocol_stats = {"http": 0, "https": 0}

    from pymongo import UpdateOne, UpdateMany
    upsert_ops = []
    dns_updates = []

    for i, result in enumerate(results):
        domain = domains_to_check[i]
        if isinstance(result, Exception):
            result = {
                "domain": domain,
                "status": "error",
                "status_code": None,
                "response_time_ms": None,
                "resolved_ip": None,
                "probe_ip": probe_ip,
                "error": str(result)[:200],
                "checked_at": now,
            }

        if not result.get("probe_ip"):
            result["probe_ip"] = probe_ip

        # Track protocol
        protocol = result.pop("_protocol", None)
        dns_ms = result.pop("_dns_ms", 0)
        if dns_ms:
            dns_times.append(dns_ms)
        if protocol:
            protocol_stats[protocol] = protocol_stats.get(protocol, 0) + 1

        doc = {
            "rule_id": rule_id,
            "rule_name": rule_name,
            "checked_at": now,
            "last_protocol": protocol or "http",
            "record_type": result.get("record_type", "unknown"),
            **result,
        }
        upsert_ops.append(UpdateOne(
            {"rule_id": rule_id, "domain": domain},
            {"$set": doc},
            upsert=True,
        ))

        if dns_col is not None:
            dns_updates.append(UpdateMany(
                {"name": result["domain"]},
                {"$set": {
                    "last_status": result.get("status"),
                    "last_status_code": result.get("status_code"),
                    "last_response_time_ms": result.get("response_time_ms"),
                    "last_resolved_ip": result.get("resolved_ip"),
                    "last_probe_ip": result.get("probe_ip"),
                    "last_checked_at": now,
                }}
            ))

        if result["status"] == "up":
            up_count += 1
            if result.get("response_time_ms"):
                up_times.append(result["response_time_ms"])
        elif result["status"] == "timeout":
            down_count += 1
        elif result["status"] in ("3xx", "4xx", "5xx"):
            error_count += 1
        else:
            error_count += 1

    logger.info(f"[Step 7] up={up_count}, down={down_count}, error={error_count}")
    logger.info(f"[Step 7] Protocol: http={protocol_stats.get('http',0)}, https={protocol_stats.get('https',0)}")

    # ── Step 8: Bulk write ──
    if upsert_ops:
        await collection.bulk_write(upsert_ops)
        logger.info(f"[Step 8] Upserted {len(upsert_ops)} results")

    if dns_updates:
        await dns_col.bulk_write(dns_updates)
        logger.info(f"[Step 8] Updated {len(dns_updates)} dns_domains")

    if domain_client_mongo:
        domain_client_mongo.close()

    # ── Step 9: Update rule status ──
    overall_status = "ok" if down_count == 0 and error_count == 0 else "warning" if up_count > 0 else "error"
    await execute(
        "UPDATE monitor_rule SET status=%s, last_check=%s, updated_at=UTC_TIMESTAMP() WHERE id=%s",
        (overall_status, now, rule_id),
    )

    # ── Summary ──
    avg_up = round(sum(up_times) / len(up_times), 2) if up_times else 0
    max_up = round(max(up_times), 2) if up_times else 0
    avg_dns = round(sum(dns_times) / len(dns_times), 2) if dns_times else 0
    logger.info(f"[Done] '{rule_name}': up={up_count}, down={down_count}, error={error_count}, time={check_elapsed}s")
    logger.info(f"[Done] HTTP response (ms) - up: avg={avg_up} max={max_up}")
    logger.info(f"[Done] DNS resolve (ms) - avg={avg_dns}")


async def run_single_check(rule_id: int):
    """Run a single check for a specific rule"""
    from app.services.db import query_one
    from app.services.redis_lock import acquire_lock, release_lock

    lock_name = f"check:{rule_id}"
    if not await acquire_lock(lock_name):
        logger.info(f"[Monitor] Rule {rule_id} check already running, skipping")
        return

    try:
        rule = await query_one("SELECT * FROM monitor_rule WHERE id=%s AND enabled=1", (rule_id,))
        if rule:
            await run_check_for_rule(rule)
    finally:
        await release_lock(lock_name)
