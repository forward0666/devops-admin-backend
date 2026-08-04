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
    return f"account_{account_id}_zone_{zone_id}_ddos"


@router.get("")
async def list_all_rules(account_id: int):
    """Read all DDoS rules for an account from MongoDB"""
    db = await get_db()
    all_rules = []
    collection_prefix = f"account_{account_id}_zone_"
    collections = await db.list_collection_names()
    for coll_name in collections:
        if coll_name.startswith(collection_prefix) and coll_name.endswith("_ddos"):
            coll = db[coll_name]
            rows = await coll.find({"account_id": str(account_id)}).to_list(length=5000)
            for r in rows:
                r["_id"] = str(r["_id"])
            all_rules.extend(rows)
    return {"code": 200, "data": all_rules}


@zone_router.post("/sync")
async def sync_rules(
    account_id: int,
    zone_id: str,
    x_cf_token: str = Header(..., alias="X-Cf-Token"),
):
    """Fetch DDoS rules from Cloudflare API and sync to MongoDB"""
    logger.info(f"[DDoS Sync] Start sync for account_id={account_id}, zone_id={zone_id}")
    account = await query_one("SELECT id, name FROM account WHERE id = %s", (account_id,))
    if not account:
        raise HTTPException(status_code=404, detail="Account not found")

    try:
        cf_data = await cf_client.async_list_ddos_rules(x_cf_token, zone_id)
    except Exception as e:
        logger.error(f"[DDoS Sync] CF API error for zone {zone_id}: {type(e).__name__}: {e}")
        return {"code": 403, "message": f"CF API error: {type(e).__name__}"}
    if not cf_data.get("success"):
        raise HTTPException(status_code=500, detail="Failed to fetch from Cloudflare")

    rules = cf_data.get("result", [])
    now = datetime.utcnow()
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]

    synced = 0
    for rule in rules:
        doc = {
            "rule_id": rule.get("id", ""),
            "zone_id": zone_id,
            "account_id": str(account_id),
            "description": rule.get("description", ""),
            "expression": rule.get("expression", ""),
            "action": rule.get("action", "block"),
            "enabled": rule.get("enabled", True),
            "action_parameters": rule.get("action_parameters", {}),
            "position_index": rule.get("position_index", 0),
            "synced_at": now,
        }
        await collection.update_one(
            {"rule_id": rule.get("id", "")},
            {"$set": doc},
            upsert=True,
        )
        synced += 1

    stale = await collection.delete_many({"zone_id": zone_id, "synced_at": {"$lt": now}})
    logger.info(f"[DDoS Sync] Complete: synced={synced}/{len(rules)}, stale_removed={stale.deleted_count}")
    return {"code": 200, "data": {"synced": synced, "total": len(rules), "stale_removed": stale.deleted_count}}


@zone_router.get("")
async def list_rules(account_id: int, zone_id: str):
    """Read DDoS rules for a specific zone from MongoDB"""
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]
    rows = await collection.find({"account_id": str(account_id)}).to_list(length=5000)
    for r in rows:
        r["_id"] = str(r["_id"])
    return {"code": 200, "data": rows}


@zone_router.delete("/sync")
async def clear_rules(account_id: int, zone_id: str):
    """Clear synced DDoS rules from MongoDB"""
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]
    result = await collection.delete_many({})
    return {"code": 200, "data": {"deleted": result.deleted_count}}
