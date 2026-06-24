import logging
import asyncio
import httpx
import time
from datetime import datetime

from app.services.db import execute
from app.services.nacos_client import get_service_instance

logger = logging.getLogger(__name__)


async def get_service_url(service_name: str) -> str:
    """Get service URL from Nacos service discovery"""
    instance = await get_service_instance(service_name)
    if instance:
        ip = instance.get("ip", "127.0.0.1")
        port = instance.get("port", 8080)
        url = f"http://{ip}:{port}"
        logger.info(f"[Nacos] Resolved {service_name} -> {url}")
        return url
    logger.warning(f"[Nacos] No healthy instance for {service_name}, using fallback")
    return "http://127.0.0.1:8080"


async def get_cf_accounts(cf_url: str) -> list:
    """Fetch all accounts from cloudflare service"""
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.get(f"{cf_url}/accounts", params={"raw": True})
        if resp.status_code == 200:
            return resp.json().get("data", [])
    return []


async def get_cf_zones(cf_url: str, account_id: int, api_key: str) -> list:
    """Fetch all zones for an account from cloudflare service"""
    headers = {"X-Cf-Token": api_key}
    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.get(f"{cf_url}/zones", headers=headers, params={"account_id": account_id})
        if resp.status_code == 200:
            data = resp.json().get("data", [])
            if isinstance(data, list):
                # Ensure each zone has 'id' field
                for z in data:
                    if isinstance(z, dict) and "id" not in z and "zone_id" in z:
                        z["id"] = z["zone_id"]
                return data
            elif isinstance(data, dict):
                return data.get("result", [])
    logger.warning(f"[get_cf_zones] Failed: account_id={account_id}, status={resp.status_code}")
    return []


async def run_check_domain(task: dict):
    """Execute a check_domain task by calling Monitor service API"""
    start_time = time.monotonic()
    task_id = task.get("id")
    task_name = task.get("name", "")
    config = task.get("config", {})
    rule_ids = config.get("rule_ids", [])
    if not rule_ids:
        logger.warning(f"[Task] check_domain '{task_name}': no rule_ids in config")
        return


    monitor_url = await get_service_url("monitor")

    async with httpx.AsyncClient(timeout=300) as client:
        for rule_id in rule_ids:
            try:
                resp = await client.post(f"{monitor_url}/rules/{rule_id}/check")
                if resp.status_code == 200:
                    logger.info(f"[Task] check_domain '{task_name}': triggered rule {rule_id}")
                else:
                    logger.warning(f"[Task] check_domain '{task_name}': rule {rule_id} returned {resp.status_code}")
            except Exception as e:
                logger.error(f"[Task] check_domain '{task_name}': failed to trigger rule {rule_id}: {e}")

    await execute("UPDATE task SET last_run_at=UTC_TIMESTAMP(), last_status=%s WHERE id=%s", ("success", task_id))
    elapsed = round(time.monotonic() - start_time, 2)
    logger.info(f"[Task] ✅ check_domain '{task_name}' completed in {elapsed}s")


async def run_sync_zone(task: dict):
    """Sync Cloudflare zones"""
    start_time = time.monotonic()
    task_id = task.get("id")
    task_name = task.get("name", "")
    config = task.get("config", {})
    account_ids = config.get("account_ids", [])
    sync_all = not account_ids or "all" in account_ids


    cf_url = await get_service_url("cloudflare")
    accounts = await get_cf_accounts(cf_url)
    account_key_map = {a["id"]: a.get("api_key") for a in accounts if a.get("id")}

    if sync_all:
        account_ids = list(account_key_map.keys())

    async with httpx.AsyncClient(timeout=300) as client:
        async def sync_one(acc_id: int):
            api_key = account_key_map.get(acc_id)
            if not api_key:
                return
            url = f"{cf_url}/zones/sync?account_id={acc_id}"
            logger.info(f"[Task] sync_zone: POST {url}")
            try:
                resp = await client.post(url, headers={"X-Cf-Token": api_key})
                if resp.status_code == 200:
                    data = resp.json().get("data", {})
                    logger.info(f"[Task] sync_zone '{task_name}': account {acc_id} synced {data.get('synced', 0)}/{data.get('total', 0)}")
                else:
                    logger.warning(f"[Task] sync_zone '{task_name}': account {acc_id} returned {resp.status_code}")
            except Exception as e:
                logger.error(f"[Task] sync_zone '{task_name}': failed for account {acc_id}: {e}")

        await asyncio.gather(*[sync_one(aid) for aid in account_ids])

    await execute("UPDATE task SET last_run_at=UTC_TIMESTAMP(), last_status=%s WHERE id=%s", ("success", task_id))
    elapsed = round(time.monotonic() - start_time, 2)
    logger.info(f"[Task] ✅ sync_zone '{task_name}' completed in {elapsed}s")


async def run_sync_dns(task: dict):
    """Sync Cloudflare DNS records for all zones"""
    start_time = time.monotonic()
    task_id = task.get("id")
    task_name = task.get("name", "")
    config = task.get("config", {})
    account_ids = config.get("account_ids", [])
    sync_all = not account_ids or "all" in account_ids


    cf_url = await get_service_url("cloudflare")
    accounts = await get_cf_accounts(cf_url)
    account_key_map = {a["id"]: a.get("api_key") for a in accounts if a.get("id")}

    if sync_all:
        account_ids = list(account_key_map.keys())

    async with httpx.AsyncClient(timeout=300) as client:
        async def sync_one(acc_id: int):
            api_key = account_key_map.get(acc_id)
            if not api_key:
                return
            url = f"{cf_url}/dns/sync?account_id={acc_id}"
            logger.info(f"[Task] sync_dns: POST {url}")
            try:
                resp = await client.post(url, headers={"X-Cf-Token": api_key})
                if resp.status_code == 200:
                    data = resp.json().get("data", {})
                    logger.info(f"[Task] sync_dns '{task_name}': account {acc_id} synced {data.get('synced', 0)}/{data.get('total', 0)}")
                else:
                    logger.warning(f"[Task] sync_dns '{task_name}': account {acc_id} returned {resp.status_code}")
            except Exception as e:
                logger.error(f"[Task] sync_dns '{task_name}': failed for account {acc_id}: {e}")

        await asyncio.gather(*[sync_one(aid) for aid in account_ids])

    await execute("UPDATE task SET last_run_at=UTC_TIMESTAMP(), last_status=%s WHERE id=%s", ("success", task_id))
    elapsed = round(time.monotonic() - start_time, 2)
    logger.info(f"[Task] ✅ sync_dns '{task_name}' completed in {elapsed}s")


async def run_sync_security(task: dict):
    """Sync Cloudflare security rules for all zones"""
    start_time = time.monotonic()
    task_id = task.get("id")
    task_name = task.get("name", "")
    config = task.get("config", {})
    account_ids = config.get("account_ids", [])
    sync_all = not account_ids or "all" in account_ids


    cf_url = await get_service_url("cloudflare")
    accounts = await get_cf_accounts(cf_url)
    account_key_map = {a["id"]: a.get("api_key") for a in accounts if a.get("id")}

    if sync_all:
        account_ids = list(account_key_map.keys())

    async with httpx.AsyncClient(timeout=300) as client:
        async def sync_one_zone(acc_id: int, zone_id: str, headers: dict):
            url = f"{cf_url}/zones/{zone_id}/security/sync?account_id={acc_id}"
            logger.info(f"[Task] sync_security: POST {url}")
            try:
                resp = await client.post(url, headers=headers)
                if resp.status_code == 200:
                    logger.info(f"[Task] sync_security '{task_name}': zone {zone_id} synced")
                else:
                    logger.warning(f"[Task] sync_security '{task_name}': zone {zone_id} returned {resp.status_code}")
            except Exception as e:
                logger.error(f"[Task] sync_security '{task_name}': failed for zone {zone_id}: {e}")

        async def sync_one_acc(acc_id: int):
            api_key = account_key_map.get(acc_id)
            if not api_key:
                return
            headers = {"X-Cf-Token": api_key}
            zones = await get_cf_zones(cf_url, acc_id, api_key)
            logger.info(f"[Task] sync_security '{task_name}': account {acc_id} has {len(zones)} zones")
            zone_ids = [z.get("zone_id") or z.get("id") if isinstance(z, dict) else z for z in zones]
            await asyncio.gather(*[sync_one_zone(acc_id, zid, headers) for zid in zone_ids if zid])

        await asyncio.gather(*[sync_one_acc(aid) for aid in account_ids])

    await execute("UPDATE task SET last_run_at=UTC_TIMESTAMP(), last_status=%s WHERE id=%s", ("success", task_id))
    elapsed = round(time.monotonic() - start_time, 2)
    logger.info(f"[Task] ✅ sync_security '{task_name}' completed in {elapsed}s")


async def run_sync_cache(task: dict):
    """Sync Cloudflare cache rules for all zones"""
    start_time = time.monotonic()
    task_id = task.get("id")
    task_name = task.get("name", "")
    config = task.get("config", {})
    account_ids = config.get("account_ids", [])
    sync_all = not account_ids or "all" in account_ids


    cf_url = await get_service_url("cloudflare")
    accounts = await get_cf_accounts(cf_url)
    account_key_map = {a["id"]: a.get("api_key") for a in accounts if a.get("id")}

    if sync_all:
        account_ids = list(account_key_map.keys())

    async with httpx.AsyncClient(timeout=300) as client:
        async def sync_one_zone(acc_id: int, zone_id: str, headers: dict):
            url = f"{cf_url}/zones/{zone_id}/cache/sync?account_id={acc_id}"
            logger.info(f"[Task] sync_cache: POST {url}")
            try:
                resp = await client.post(url, headers=headers)
                if resp.status_code == 200:
                    logger.info(f"[Task] sync_cache '{task_name}': zone {zone_id} synced")
                else:
                    logger.warning(f"[Task] sync_cache '{task_name}': zone {zone_id} returned {resp.status_code}")
            except Exception as e:
                logger.error(f"[Task] sync_cache '{task_name}': failed for zone {zone_id}: {e}")

        async def sync_one_acc(acc_id: int):
            api_key = account_key_map.get(acc_id)
            if not api_key:
                return
            headers = {"X-Cf-Token": api_key}
            zones = await get_cf_zones(cf_url, acc_id, api_key)
            logger.info(f"[Task] sync_cache '{task_name}': account {acc_id} has {len(zones)} zones")
            zone_ids = [z.get("zone_id") or z.get("id") if isinstance(z, dict) else z for z in zones]
            await asyncio.gather(*[sync_one_zone(acc_id, zid, headers) for zid in zone_ids if zid])

        await asyncio.gather(*[sync_one_acc(aid) for aid in account_ids])

    await execute("UPDATE task SET last_run_at=UTC_TIMESTAMP(), last_status=%s WHERE id=%s", ("success", task_id))
    elapsed = round(time.monotonic() - start_time, 2)
    logger.info(f"[Task] ✅ sync_cache '{task_name}' completed in {elapsed}s")


async def run_sync_rule(task: dict):
    """Trigger sync rule push for selected sync rules"""
    start_time = time.monotonic()
    task_id = task.get("id")
    task_name = task.get("name", "")
    config = task.get("config", {})
    sync_rule_ids = config.get("sync_rule_ids", [])
    if not sync_rule_ids:
        logger.warning(f"[Task] sync_rule '{task_name}': no sync_rule_ids in config")
        return

    cf_url = await get_service_url("cloudflare")
    failed = 0

    async with httpx.AsyncClient(timeout=300) as client:
        for rule_id in sync_rule_ids:
            try:
                url = f"{cf_url}/syncRules/{rule_id}/push"
                logger.info(f"[Task] sync_rule: POST {url}")
                resp = await client.post(url)
                if resp.status_code == 200:
                    logger.info(f"[Task] sync_rule '{task_name}': rule {rule_id} pushed")
                else:
                    failed += 1
                    logger.warning(f"[Task] sync_rule '{task_name}': rule {rule_id} returned {resp.status_code}")
            except BaseException as e:
                failed += 1
                logger.error(f"[Task] sync_rule '{task_name}': failed for rule {rule_id}: {type(e).__name__}: {e}")

    status = "success" if failed == 0 else f"failed {failed}/{len(sync_rule_ids)}"
    await execute("UPDATE task SET last_run_at=UTC_TIMESTAMP(), last_status=%s WHERE id=%s", (status, task_id))
    elapsed = round(time.monotonic() - start_time, 2)
    logger.info(f"[Task] ✅ sync_rule '{task_name}' completed in {elapsed}s, status={status}")


async def run_sync_domain(task: dict):
    """Sync domains (dns_records -> domain collection)"""
    start_time = time.monotonic()
    task_id = task.get("id")
    task_name = task.get("name", "")

    domain_url = await get_service_url("domain")
    url = f"{domain_url}/domain/sync"
    logger.info(f"[Task] sync_domain: POST {url}")

    async with httpx.AsyncClient(timeout=300) as client:
        try:
            resp = await client.post(url)
            if resp.status_code == 200:
                data = resp.json().get("data", {})
                logger.info(f"[Task] sync_domain '{task_name}': synced={data.get('synced', 0)}")
            else:
                logger.warning(f"[Task] sync_domain '{task_name}': returned {resp.status_code}")
        except Exception as e:
            logger.error(f"[Task] sync_domain '{task_name}': failed: {e}")

    await execute("UPDATE task SET last_run_at=UTC_TIMESTAMP(), last_status=%s WHERE id=%s", ("success", task_id))
    elapsed = round(time.monotonic() - start_time, 2)
    logger.info(f"[Task] ✅ sync_domain '{task_name}' completed in {elapsed}s")


async def run_sync_project_domain(task: dict):
    """Sync project domains: trigger check on selected or all enabled sync_domain rules"""
    start_time = time.monotonic()
    task_id = task.get("id")
    task_name = task.get("name", "")
    config = task.get("config", {})
    rule_ids = config.get("sync_project_domain_rule_ids", [])

    cf_url = await get_service_url("cloudflare")
    url = f"{cf_url}/sync_domain/rules"
    logger.info(f"[Task] sync_project_domain: GET {url}")

    async with httpx.AsyncClient(timeout=300) as client:
        try:
            resp = await client.get(url)
            if resp.status_code != 200:
                logger.warning(f"[Task] sync_project_domain '{task_name}': GET rules returned {resp.status_code}")
                return
            all_rules = resp.json().get("data", [])

            # -1 means all enabled rules
            if -1 in rule_ids:
                rules_to_run = [r for r in all_rules if r.get("enabled")]
            else:
                rules_to_run = [r for r in all_rules if r["id"] in rule_ids]

            logger.info(f"[Task] sync_project_domain '{task_name}': {len(rules_to_run)} rules to run")

            synced_total = 0
            for rule in rules_to_run:
                rule_id = rule["id"]
                check_url = f"{cf_url}/sync_domain/rules/{rule_id}/check"
                try:
                    r = await client.post(check_url)
                    if r.status_code == 200:
                        synced = r.json().get("data", {}).get("synced", 0)
                        synced_total += synced
                        logger.info(f"[Task] sync_project_domain '{task_name}': rule {rule_id} synced {synced}")
                    else:
                        logger.warning(f"[Task] sync_project_domain '{task_name}': rule {rule_id} returned {r.status_code}")
                except Exception as e:
                    logger.error(f"[Task] sync_project_domain '{task_name}': rule {rule_id} failed: {e}")

            logger.info(f"[Task] sync_project_domain '{task_name}': total synced={synced_total}")
        except Exception as e:
            logger.error(f"[Task] sync_project_domain '{task_name}': failed: {e}")

    await execute("UPDATE task SET last_run_at=UTC_TIMESTAMP(), last_status=%s WHERE id=%s", ("success", task_id))
    elapsed = round(time.monotonic() - start_time, 2)
    logger.info(f"[Task] ✅ sync_project_domain '{task_name}' completed in {elapsed}s")


# Registry: task type -> executor
TASK_EXECUTORS = {
    "check_domain": run_check_domain,
    "sync_zone": run_sync_zone,
    "sync_dns": run_sync_dns,
    "sync_security": run_sync_security,
    "sync_cache": run_sync_cache,
    "sync_domain": run_sync_domain,
    "sync_rule": run_sync_rule,
    "sync_project_domain": run_sync_project_domain,
}


async def execute_task(task: dict):
    """Execute a task by its type"""
    import json
    if isinstance(task.get("config"), str):
        try:
            task["config"] = json.loads(task["config"])
        except Exception:
            task["config"] = {}
    task_type = task.get("type", "")
    executor = TASK_EXECUTORS.get(task_type)
    if executor:
        await executor(task)
    else:
        logger.warning(f"[Task] No executor for type: {task_type}")
