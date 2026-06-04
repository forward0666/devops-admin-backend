import logging
import asyncio
import httpx
import time
import socket
from datetime import datetime
import aiodns

from app.services.db import query_all, execute
from app.services.mongodb import get_db

logger = logging.getLogger(__name__)

# Fast DNS resolver (Google 8.8.8.8)
_dns_resolver: aiodns.DNSResolver = None
_dns_cache: dict[str, str] = {}


def _get_dns_resolver() -> aiodns.DNSResolver:
    global _dns_resolver
    if _dns_resolver is None:
        _dns_resolver = aiodns.DNSResolver(nameservers=["8.8.8.8", "8.8.4.4"], timeout=5)
    return _dns_resolver


async def resolve_domain(domain: str) -> str | None:
    """Resolve domain using aiodns with cache"""
    if domain in _dns_cache:
        return _dns_cache[domain]
    try:
        resolver = _get_dns_resolver()
        result = await resolver.query(domain, "A")
        if result.addresses:
            ip = result.addresses[0]
            _dns_cache[domain] = ip
            return ip
    except Exception:
        pass
    return None

# Cache probe IP (monitor server's exit IP)
_probe_ip: str = None


def get_probe_ip() -> str:
    """Get monitor server's exit IP (cached)"""
    global _probe_ip
    if _probe_ip:
        return _probe_ip
    try:
        import httpx
        resp = httpx.get('https://ipinfo.io/json', timeout=10, verify=False)
        _probe_ip = resp.json().get('ip', 'fail')
        return _probe_ip
    except Exception as e:
        logger.warning(f"Failed to get probe IP from ipinfo.io: {e}")
        return "fail"


async def async_get_probe_ip() -> str:
    """Async wrapper for get_probe_ip"""
    return await asyncio.to_thread(get_probe_ip)

# Track running tasks
_running_tasks: dict[int, asyncio.Task] = {}


_shared_client: httpx.AsyncClient = None


def _get_shared_client() -> httpx.AsyncClient:
    global _shared_client
    if _shared_client is None or _shared_client.is_closed:
        _shared_client = httpx.AsyncClient(
            timeout=15,
            follow_redirects=False,
            limits=httpx.Limits(max_connections=1000, max_keepalive_connections=200),
            verify=False,
        )
    return _shared_client


def _close_shared_client():
    global _shared_client
    if _shared_client and not _shared_client.is_closed:
        import asyncio
        asyncio.get_event_loop().create_task(_shared_client.aclose())
        _shared_client = None


async def check_domain(domain: str, timeout: int = 15) -> dict:
    """Check a single domain and return result"""
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

    # Resolve domain IP (fast async DNS)
    result["resolved_ip"] = await resolve_domain(domain)

    url = f"https://{domain}"
    start = time.monotonic()

    try:
        client = _get_shared_client()
        resp = await client.head(url)
        elapsed = (time.monotonic() - start) * 1000
        result["status_code"] = resp.status_code
        result["response_time_ms"] = round(elapsed, 2)
        if resp.status_code < 400:
            result["status"] = "up"
        else:
            result["status"] = "error"
    except httpx.ConnectError as e:
        elapsed = (time.monotonic() - start) * 1000
        result["response_time_ms"] = round(elapsed, 2)
        result["error"] = f"Connection failed: {e}"
    except httpx.TimeoutException:
        elapsed = (time.monotonic() - start) * 1000
        result["response_time_ms"] = round(elapsed, 2)
        result["error"] = "Timeout"
    except Exception as e:
        elapsed = (time.monotonic() - start) * 1000
        result["response_time_ms"] = round(elapsed, 2)
        result["error"] = str(e)[:200]

    return result


async def run_check_for_rule(rule: dict):
    """Run check for a single rule and save results to MongoDB"""
    rule_id = rule["id"]
    rule_name = rule["name"]
    source = rule["source"]
    domains = rule.get("domains", [])
    custom_domains = rule.get("custom_domains", "")

    # Collect all domains to check
    domains_to_check = []

    if source == "custom" and custom_domains:
        # Custom domains: one per line
        for line in custom_domains.strip().split("\n"):
            d = line.strip()
            if d:
                domains_to_check.append(d)
    elif domains:
        if "all" in domains:
            # Fetch all domains from cloudflare dnsDomain collection
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

    logger.info(f"[Monitor] Checking {len(domains_to_check)} domains for rule '{rule_name}'")

    # Update rule status to running
    await execute("UPDATE monitor_rule SET status='running', updated_at=NOW() WHERE id=%s", (rule_id,))

    # All domains in parallel with concurrency limit
    MAX_CONCURRENT = 500
    db = await get_db()
    collection = db[f"monitor_results_{rule_id}"]
    await collection.create_index([("rule_id", 1), ("domain", 1)], unique=True, background=True)
    now = datetime.utcnow()
    up_count = 0
    down_count = 0
    error_count = 0
    probe_ip = await async_get_probe_ip()

    # Get cloudflare db for updating dns_domains
    cf_client_mongo = None
    dns_col = None
    try:
        from motor.motor_asyncio import AsyncIOMotorClient
        from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_AUTH_DB
        uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/cloudflare?authSource={MONGODB_AUTH_DB}"
        cf_client_mongo = AsyncIOMotorClient(uri)
        dns_col = cf_client_mongo["cloudflare"]["dns_domains"]
    except Exception as e:
        logger.error(f"[Monitor] Failed to connect cloudflare DB: {e}")

    sem = asyncio.Semaphore(MAX_CONCURRENT)

    async def checked(domain: str) -> dict:
        async with sem:
            return await check_domain(domain)

    # Fire all checks concurrently
    tasks = [checked(d) for d in domains_to_check]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    # Bulk write results - upsert by (rule_id + domain)
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

        if dns_col is not None and result.get("domain"):
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
        elif result["status"] == "down":
            down_count += 1
        else:
            error_count += 1

    # Batch upsert results
    if upsert_ops:
        res = await collection.bulk_write(upsert_ops)
        logger.info(f"[Monitor] Rule '{rule_name}': upserted {len(upsert_ops)} results (matched={res.matched_count}, upserted={res.upserted_count})")

    # Batch update dns_domains
    if dns_updates:
        await dns_col.bulk_write(dns_updates)
        logger.info(f"[Monitor] Rule '{rule_name}': updated {len(dns_updates)} dns_domains")

    if cf_client_mongo:
        cf_client_mongo.close()

    # Update rule last_check and status
    overall_status = "ok" if down_count == 0 and error_count == 0 else "warning" if up_count > 0 else "error"
    await execute(
        "UPDATE monitor_rule SET status=%s, last_check=%s, updated_at=NOW() WHERE id=%s",
        (overall_status, now, rule_id),
    )

    logger.info(f"[Monitor] Rule '{rule_name}' done: up={up_count}, down={down_count}, error={error_count}")


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



