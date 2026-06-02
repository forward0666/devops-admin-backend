import logging
import asyncio
import httpx
import time
from datetime import datetime

from app.services.db import query_all, execute
from app.services.mongodb import get_db

logger = logging.getLogger(__name__)

# Track running tasks
_running_tasks: dict[int, asyncio.Task] = {}


async def check_domain(domain: str, timeout: int = 10) -> dict:
    """Check a single domain and return result"""
    result = {
        "domain": domain,
        "status": "down",
        "status_code": None,
        "response_time_ms": None,
        "error": None,
        "checked_at": datetime.utcnow(),
    }

    url = f"https://{domain}"
    start = time.monotonic()

    try:
        async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
            resp = await client.get(url)
            elapsed = (time.monotonic() - start) * 1000
            result["status_code"] = resp.status_code
            result["response_time_ms"] = round(elapsed, 2)
            result["status"] = "up" if resp.status_code < 500 else "error"
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
                db = await get_db()
                collections = await db.list_collection_names()
                for col_name in collections:
                    if col_name.endswith("_dns_records"):
                        col = db[col_name]
                        docs = await col.find({}, {"name": 1}).to_list(length=10000)
                        for doc in docs:
                            name = doc.get("name", "")
                            if name and name not in domains_to_check:
                                domains_to_check.append(name)
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

    # Check all domains concurrently
    tasks = [check_domain(d) for d in domains_to_check]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    # Save results to MongoDB
    db = await get_db()
    collection = db[f"monitor_results_{rule_id}"]
    now = datetime.utcnow()

    up_count = 0
    down_count = 0
    error_count = 0

    for i, result in enumerate(results):
        if isinstance(result, Exception):
            result = {
                "domain": domains_to_check[i],
                "status": "error",
                "status_code": None,
                "response_time_ms": None,
                "error": str(result)[:200],
                "checked_at": now,
            }

        doc = {
            "rule_id": rule_id,
            "rule_name": rule_name,
            "source": source,
            "checked_at": now,
            **result,
        }

        await collection.insert_one(doc)

        if result["status"] == "up":
            up_count += 1
        elif result["status"] == "down":
            down_count += 1
        else:
            error_count += 1

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
    rule = await query_one("SELECT * FROM monitor_rule WHERE id=%s AND enabled=1", (rule_id,))
    if rule:
        # Parse domains
        if isinstance(rule.get("domains"), str):
            import json
            try:
                rule["domains"] = json.loads(rule["domains"])
            except:
                rule["domains"] = []
        await run_check_for_rule(rule)


async def scheduler_loop():
    """Background scheduler: check rules based on their interval"""
    logger.info("[Monitor] Scheduler started")
    while True:
        try:
            rules = await query_all("SELECT * FROM monitor_rule WHERE enabled=1")
            now = datetime.utcnow()

            for rule in rules:
                rule_id = rule["id"]
                interval = rule.get("check_interval", 5)  # minutes
                last_check = rule.get("last_check")

                # Parse domains
                if isinstance(rule.get("domains"), str):
                    import json
                    try:
                        rule["domains"] = json.loads(rule["domains"])
                    except:
                        rule["domains"] = []

                # Check if it's time to run
                should_run = False
                if last_check is None:
                    should_run = True
                else:
                    diff = (now - last_check).total_seconds() / 60
                    if diff >= interval:
                        should_run = True

                if should_run:
                    # Run check in background
                    if rule_id not in _running_tasks or _running_tasks[rule_id].done():
                        _running_tasks[rule_id] = asyncio.create_task(run_check_for_rule(rule))

        except Exception as e:
            logger.error(f"[Monitor] Scheduler error: {e}")

        # Sleep 30 seconds between checks
        await asyncio.sleep(30)
