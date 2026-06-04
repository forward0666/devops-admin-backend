from fastapi import APIRouter, Header, HTTPException
from datetime import datetime
import logging

from app.services.db import query_one
from app.services.mongodb import get_db
from app.services import cf_client

logger = logging.getLogger(__name__)
router = APIRouter()
zone_router = APIRouter()


def get_collection_name(account_id: int, zone_id: str) -> str:
    return f"account_{account_id}_zone_{zone_id}_cache"


# --- Top-level routes (no zone_id) ---

@router.get("")
async def list_all_cache(account_id: int):
    """Read all cache purge logs for an account from MongoDB"""
    db = await get_db()
    all_logs = []
    collection_prefix = f"account_{account_id}_zone_"
    collections = await db.list_collection_names()
    for coll_name in collections:
        if coll_name.startswith(collection_prefix) and coll_name.endswith("_cache"):
            coll = db[coll_name]
            rows = await coll.find({"account_id": str(account_id)}).sort("timestamp", -1).to_list(length=5000)
            for r in rows:
                r["_id"] = str(r["_id"])
            all_logs.extend(rows)
    return {"code": 200, "data": all_logs}


# --- Zone-level routes ---

@zone_router.post("/sync")
async def sync_cache_rules(
    account_id: int,
    zone_id: str,
    x_cf_token: str = Header(..., alias="X-Cf-Token"),
):
    """Fetch cache rules from Cloudflare API and sync to MongoDB"""
    logger.info(f"[Cache Sync] Start sync for account_id={account_id}, zone_id={zone_id}")
    account = await query_one("SELECT id, name FROM account WHERE id = %s", (account_id,))
    if not account:
        raise HTTPException(status_code=404, detail="Account not found")

    try:
        cf_data = await cf_client.async_list_cache_rules(x_cf_token, zone_id)
    except Exception as e:
        logger.error(f"[Cache Sync] CF API error for zone {zone_id}: {type(e).__name__}: {e}")
        return {"code": 403, "message": f"CF API error: {type(e).__name__}"}
    if not cf_data.get("success"):
        raise HTTPException(status_code=500, detail="Failed to fetch from Cloudflare")

    rules = cf_data.get("result", [])
    now = datetime.utcnow()
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]

    synced = 0
    for rule in rules:
        action_params = rule.get("action_parameters", {}) or {}
        cache_status = action_params.get("cache", "")
        edge_ttl = action_params.get("edge_ttl", {})
        browser_ttl = action_params.get("browser_ttl", {})

        doc = {
            "rule_id": rule["id"],
            "zone_id": zone_id,
            "account_id": str(account_id),
            "description": rule.get("description", ""),
            "expression": rule.get("expression", ""),
            "action": cache_status or "cache_rule",
            "edge_ttl": edge_ttl,
            "browser_ttl": browser_ttl,
            "status": rule.get("status", "active"),
            "last_updated": rule.get("last_updated", ""),
            "enabled": rule.get("enabled", True),
            "synced_at": now,
        }
        await collection.update_one(
            {"rule_id": rule["id"]},
            {"$set": doc},
            upsert=True,
        )
        synced += 1

    logger.info(f"[Cache Sync] Complete for account_id={account_id}, zone_id={zone_id}: synced={synced}/{len(rules)}")
    return {"code": 200, "data": {"synced": synced, "total": len(rules)}}


@zone_router.post("/purge")
async def purge_all(
    account_id: int,
    zone_id: str,
    x_cf_token: str = Header(..., alias="X-Cf-Token"),
):
    """Purge all cache and log to MongoDB"""
    logger.info(f"[Cache Purge] Purge all for account_id={account_id}, zone_id={zone_id}")
    await cf_client.async_purge_all(x_cf_token, zone_id)
    await _log_purge(account_id, zone_id, "Purge All", "All cached files")
    logger.info(f"[Cache Purge] Purge all complete for zone_id={zone_id}")
    return {"code": 200, "data": {"success": True}}


@zone_router.post("/purge/urls")
async def purge_urls(
    account_id: int,
    zone_id: str,
    x_cf_token: str = Header(..., alias="X-Cf-Token"),
    body: dict = None,
):
    files = (body or {}).get("files", [])
    logger.info(f"[Cache Purge] Purge URLs for zone_id={zone_id}: {len(files)} urls")
    await cf_client.async_purge_by_urls(x_cf_token, zone_id, files)
    await _log_purge(account_id, zone_id, "Purge URL", ", ".join(files))
    return {"code": 200, "data": {"success": True}}


@zone_router.post("/purge/tags")
async def purge_tags(
    account_id: int,
    zone_id: str,
    x_cf_token: str = Header(..., alias="X-Cf-Token"),
    body: dict = None,
):
    tags = (body or {}).get("tags", [])
    logger.info(f"[Cache Purge] Purge tags for zone_id={zone_id}: {tags}")
    await cf_client.async_purge_by_tags(x_cf_token, zone_id, tags)
    await _log_purge(account_id, zone_id, "Purge Tag", ", ".join(tags))
    return {"code": 200, "data": {"success": True}}


@zone_router.post("/purge/hosts")
async def purge_hosts(
    account_id: int,
    zone_id: str,
    x_cf_token: str = Header(..., alias="X-Cf-Token"),
    body: dict = None,
):
    hosts = (body or {}).get("hosts", [])
    logger.info(f"[Cache Purge] Purge hosts for zone_id={zone_id}: {hosts}")
    await cf_client.async_purge_by_hosts(x_cf_token, zone_id, hosts)
    await _log_purge(account_id, zone_id, "Purge Host", ", ".join(hosts))
    return {"code": 200, "data": {"success": True}}


@zone_router.post("/purge/prefixes")
async def purge_prefixes(
    account_id: int,
    zone_id: str,
    x_cf_token: str = Header(..., alias="X-Cf-Token"),
    body: dict = None,
):
    prefixes = (body or {}).get("prefixes", [])
    logger.info(f"[Cache Purge] Purge prefixes for zone_id={zone_id}: {len(prefixes)} prefixes")
    await cf_client.async_purge_by_prefixes(x_cf_token, zone_id, prefixes)
    await _log_purge(account_id, zone_id, "Purge Prefix", ", ".join(prefixes))
    return {"code": 200, "data": {"success": True}}


@zone_router.get("")
async def list_cache_logs(account_id: int, zone_id: str):
    """Read cache purge logs for a specific zone from MongoDB"""
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]
    rows = await collection.find({"account_id": str(account_id)}).sort("timestamp", -1).to_list(length=500)
    for r in rows:
        r["_id"] = str(r["_id"])
    return {"code": 200, "data": rows}


@zone_router.get("/rules")
async def list_cache_rules(account_id: int, zone_id: str):
    """Read cache rules for a specific zone from MongoDB"""
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]
    rows = await collection.find({"account_id": str(account_id)}).sort("synced_at", -1).to_list(length=5000)
    for r in rows:
        r["_id"] = str(r["_id"])
    return {"code": 200, "data": rows}


async def _log_purge(account_id: int, zone_id: str, log_type: str, target: str):
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]
    doc = {
        "zone_id": zone_id,
        "account_id": str(account_id),
        "type": log_type,
        "target": target,
        "timestamp": datetime.utcnow(),
    }
    await collection.insert_one(doc)
