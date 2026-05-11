from fastapi import APIRouter, Header, HTTPException
from datetime import datetime

from app.services.db import query_one
from app.services.mongodb import get_db
from app.services import cf_client

router = APIRouter()


def get_collection_name(account_id: int, zone_id: str) -> str:
    return f"account_{account_id}_zone_{zone_id}_security"


@router.post("/sync")
async def sync_rules(
    account_id: int,
    zone_id: str,
    x_cf_token: str = Header(..., alias="X-Cf-Token"),
):
    """Fetch security rules from Cloudflare API and sync to MongoDB"""
    account = await query_one("SELECT id, name FROM account WHERE id = %s", (account_id,))
    if not account:
        raise HTTPException(status_code=404, detail="Account not found")

    cf_data = cf_client.list_firewall_rules(x_cf_token, zone_id)
    if not cf_data.get("success"):
        raise HTTPException(status_code=500, detail="Failed to fetch from Cloudflare")

    rules = cf_data.get("result", [])
    now = datetime.utcnow()
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]

    synced = 0
    for rule in rules:
        doc = {
            "rule_id": rule["id"],
            "zone_id": zone_id,
            "account_id": str(account_id),
            "description": rule.get("description", ""),
            "expression": rule.get("expression", ""),
            "action": rule.get("action", "block"),
            "priority": rule.get("priority", 0),
            "paused": rule.get("paused", False),
            "synced_at": now,
        }
        await collection.update_one(
            {"rule_id": rule["id"]},
            {"$set": doc},
            upsert=True,
        )
        synced += 1

    return {"code": 200, "data": {"synced": synced, "total": len(rules)}}


@router.get("")
async def list_rules(account_id: int, zone_id: str):
    """Read security rules from MongoDB"""
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]
    rows = await collection.find({"account_id": str(account_id)}).sort("priority", 1).to_list(length=5000)
    for r in rows:
        r["_id"] = str(r["_id"])
    return {"code": 200, "data": rows}


@router.delete("/sync")
async def clear_rules(account_id: int, zone_id: str):
    """Clear synced security rules from MongoDB"""
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]
    result = await collection.delete_many({})
    return {"code": 200, "data": {"deleted": result.deleted_count}}
