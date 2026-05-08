from fastapi import APIRouter, Header, HTTPException
from datetime import datetime

from app.services.db import query_one
from app.services.mongodb import get_db
from app.services import cf_client

router = APIRouter()

COLLECTION = "cf_zones"


@router.post("/sync")
async def sync_zones(account_id: int, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    """Fetch zones from Cloudflare API and sync to MongoDB"""
    # Get account info
    account = await query_one("SELECT id, name, tags FROM account WHERE id = %s", (account_id,))
    if not account:
        raise HTTPException(status_code=404, detail="Account not found")

    db = await get_db()
    collection = db[COLLECTION]

    # Fetch from CF API
    cf_data = cf_client.list_zones(x_cf_token)
    if not cf_data.get("success"):
        raise HTTPException(status_code=500, detail="Failed to fetch from Cloudflare")

    zones = cf_data.get("result", [])
    now = datetime.utcnow()

    synced = 0
    for zone in zones:
        doc = {
            "zone_id": zone["id"],
            "account_id": str(account_id),
            "account_name": account["name"],
            "name": zone["name"],
            "status": zone.get("status", ""),
            "paused": zone.get("paused", False),
            "plan": zone.get("plan", {}).get("name", "") if isinstance(zone.get("plan"), dict) else str(zone.get("plan", "")),
            "name_servers": zone.get("name_servers", []),
            "ssl_mode": "",
            "synced_at": now,
        }

        await collection.update_one(
            {"zone_id": zone["id"], "account_id": str(account_id)},
            {"$set": doc},
            upsert=True,
        )
        synced += 1

    return {"code": 200, "data": {"synced": synced, "total": len(zones)}}


@router.get("")
async def list_zones(account_id: int = None):
    """Read zones from MongoDB"""
    db = await get_db()
    collection = db[COLLECTION]

    query = {}
    if account_id:
        query["account_id"] = str(account_id)

    rows = await collection.find(query).sort("name", 1).to_list(length=200)
    # Convert ObjectId to string
    for r in rows:
        r["_id"] = str(r["_id"])

    return {"code": 200, "data": rows}


@router.delete("/sync")
async def clear_zones(account_id: int = None):
    """Clear synced zone data from MongoDB"""
    db = await get_db()
    collection = db[COLLECTION]

    query = {}
    if account_id:
        query["account_id"] = str(account_id)

    result = await collection.delete_many(query)
    return {"code": 200, "data": {"deleted": result.deleted_count}}
