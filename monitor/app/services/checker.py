import logging
import asyncio
import httpx
import time
import socket
from datetime import datetime

from app.services.db import query_all, execute
from app.services.mongodb import get_db

logger = logging.getLogger(__name__)

# ─── DNS Cache (simple dict, no external lib) ───────────
_dns_cache: dict[str, str] = {}


async def resolve_domain(domain: str) -> str | None:
    """Resolve domain using system DNS with cache"""
    if domain in _dns_cache:
        return _dns_cache[domain]
    try:
        loop = asyncio.get_event_loop()
        infos = await loop.getaddrinfo(domain, None, family=socket.AF_INET)
        if infos:
            ip = infos[0][4][0]
            _dns_cache[domain] = ip
            return ip
    except Exception:
        pass
    return None


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


# ─── Shared HTTP Client ─────────────────────────────────
_client: httpx.AsyncClient = None


def _get_client() -> httpx.AsyncClient:
    """Single shared client, HTTP only (fastest)"""
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(
            timeout=5,
            follow_redirects=False,
            limits=httpx.Limits(max_connections=10000, max_keepalive_connections=2000),
        )
    return _client


def _close_client():
    global _client
    if _client and not _client.is_closed:
        asyncio.get_event_loop().create_task(_client.aclose())
        _client = None


# ─── Check Single Domain ────────────────────────────────
async def check_domain(domain: str) -> dict:
    """Check a single domain via HTTP (no TLS overhead)"""
    result = {
        "domain": domain,
        "status": "down",
        "status_code": None,
        "response_time_ms": None,
        "resolved_ip": None,
        "probe_ip": None,
        "error": None,
        "checked_at": datetime.utcnow(),
    }

    # DNS resolve (from cache or system)
    result["resolved_ip"] = await resolve_domain(domain)

    start = time.monotonic()
    client = _get_client()

    try:
        resp = await client.head(f"http://{domain}")
        elapsed = (time.monotonic() - start) * 1000
        result["status_code"] = resp.status_code
        result["response_time_ms"] = round(elapsed, 2)
        result["status"] = "up" if resp.status_code < 400 else "error"
    except (httpx.ConnectError, httpx.ConnectTimeout, httpx.TimeoutException):
        elapsed = (time.monotonic() - start) * 1000
        result["response_time_ms"] = round(elapsed, 2)
        result["error"] = "Timeout"
    except Exception as e:
        elapsed = (time.monotonic() - start) * 1000
        result["response_time_ms"] = round(elapsed, 2)
        result["error"] = str(e)[:200]

    return result


# ─── Run Check For Rule ─────────────────────────────────
async def run_check_for_rule(rule: dict):
    """Run check for a single rule and save results to MongoDB"""
    rule_id = rule["id"]
    rule_name = rule["name"]
    source = rule["source"]
    domains = rule.get("domains", [])
    custom_domains = rule.get("custom_domains", "")

    # ── Step 1: Load domains ──
    domains_to_check = []

    if source == "custom" and custom_domains:
        for line in custom_domains.strip().split("\n"):
            d = line.strip()
            if d:
                domains_to_check.append(d)
    elif domains:
        if "all" in domains:
            try:
                from motor.motor_asyncio import AsyncIOMotorClient
                from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_AUTH_DB
                uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/cloudflare?authSource={MONGODB_AUTH_DB}"
                cf_client_mongo = AsyncIOMotorClient(uri)
                cf_db = cf_client_mongo["cloudflare"]
                total_docs = await cf_db["dns_domains"].count_documents({})
                docs = await cf_db["dns_domains"].find({}, {"name": 1}).to_list(length=10000)
                seen_names = set()
                for doc in docs:
                    name = doc.get("name", "")
                    if name and name not in domains_to_check:
                        domains_to_check.append(name)
                    if name:
                        seen_names.add(name)
                empty_count = total_docs - len(seen_names)
                if empty_count > 0:
                    logger.warning(f"[Monitor] {empty_count} docs in dns_domains have empty name, skipped")
                cf_client_mongo.close()
            except Exception as e:
                logger.error(f"[Monitor] Failed to fetch all domains: {e}")
        else:
            domains_to_check = [d for d in domains if d != "all"]

    if not domains_to_check:
        logger.warning(f"[Monitor] Rule '{rule_name}' has no domains to check")
        return

    num_domains = len(domains_to_check)
    logger.info(f"[Step 1] Loaded {num_domains} domains")

    # ── Step 2: Init ──
    await execute("UPDATE monitor_rule SET status='running', updated_at=NOW() WHERE id=%s", (rule_id,))
    logger.info(f"[Step 2] Rule status set to running")

    db = await get_db()
    collection = db[f"monitor_results_{rule_id}"]
    await collection.create_index([("rule_id", 1), ("domain", 1)], unique=True, background=True)
    now = datetime.utcnow()

    probe_ip = await async_get_probe_ip()
    logger.info(f"[Step 3] Probe IP: {probe_ip}")

    # ── Step 4: Connect Cloudflare DB ──
    cf_client_mongo = None
    dns_col = None
    try:
        from motor.motor_asyncio import AsyncIOMotorClient
        from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_AUTH_DB
        uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/cloudflare?authSource={MONGODB_AUTH_DB}"
        cf_client_mongo = AsyncIOMotorClient(uri)
        dns_col = cf_client_mongo["cloudflare"]["dns_domains"]
    except Exception as e:
        logger.error(f"[Step 4] Failed to connect cloudflare DB: {e}")

    # ── Step 5: HTTP checks (all at once, no semaphore) ──
    logger.info(f"[Step 5] Starting HTTP checks for {num_domains} domains")
    check_start = time.monotonic()

    tasks = [check_domain(d) for d in domains_to_check]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    check_elapsed = round(time.monotonic() - check_start, 2)
    logger.info(f"[Step 5] HTTP checks completed in {check_elapsed}s")

    # ── Step 6: Process results ──
    up_count = 0
    down_count = 0
    error_count = 0
    up_times: list[float] = []
    down_times: list[float] = []
    error_times: list[float] = []

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

        doc = {
            "rule_id": rule_id,
            "rule_name": rule_name,
            "source": source,
            "checked_at": now,
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
        elif result["status"] == "down":
            down_count += 1
            if result.get("response_time_ms"):
                down_times.append(result["response_time_ms"])
        else:
            error_count += 1
            if result.get("response_time_ms"):
                error_times.append(result["response_time_ms"])

    logger.info(f"[Step 6] Processed: up={up_count}, down={down_count}, error={error_count}")

    # ── Step 7: Bulk write to MongoDB ──
    if upsert_ops:
        res = await collection.bulk_write(upsert_ops)
        logger.info(f"[Step 7] Upserted {len(upsert_ops)} results")

    if dns_updates:
        await dns_col.bulk_write(dns_updates)
        logger.info(f"[Step 7] Updated {len(dns_updates)} dns_domains")

    if cf_client_mongo:
        cf_client_mongo.close()

    # ── Step 8: Update rule status ──
    overall_status = "ok" if down_count == 0 and error_count == 0 else "warning" if up_count > 0 else "error"
    await execute(
        "UPDATE monitor_rule SET status=%s, last_check=%s, updated_at=NOW() WHERE id=%s",
        (overall_status, now, rule_id),
    )

    # ── Summary ──
    avg_up = round(sum(up_times) / len(up_times), 2) if up_times else 0
    max_up = round(max(up_times), 2) if up_times else 0
    logger.info(f"[Done] '{rule_name}': up={up_count}, down={down_count}, error={error_count}, time={check_elapsed}s")
    logger.info(f"[Done] Response (ms) - up: avg={avg_up} max={max_up}")


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
            if isinstance(rule.get("domains"), str):
                import json
                try:
                    rule["domains"] = json.loads(rule["domains"])
                except:
                    rule["domains"] = []
            await run_check_for_rule(rule)
    finally:
        await release_lock(lock_name)
