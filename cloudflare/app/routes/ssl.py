from fastapi import APIRouter, Header, HTTPException
from datetime import datetime

from app.services.db import query_one
from app.services.mongodb import get_db
from app.services import cf_client

router = APIRouter()
zone_router = APIRouter()


def get_collection_name(account_id: int, zone_id: str) -> str:
    return f"account_{account_id}_zone_{zone_id}_ssl"


# --- Top-level routes (no zone_id) ---

@router.get("")
async def list_all_ssl(account_id: int):
    """Read all SSL settings for an account from MongoDB"""
    db = await get_db()
    all_settings = []
    collection_prefix = f"account_{account_id}_zone_"
    collections = await db.list_collection_names()
    for coll_name in collections:
        if coll_name.startswith(collection_prefix) and coll_name.endswith("_ssl"):
            coll = db[coll_name]
            rows = await coll.find({"account_id": str(account_id)}).to_list(length=5000)
            for r in rows:
                r["_id"] = str(r["_id"])
            all_settings.extend(rows)
    return {"code": 200, "data": all_settings}


# --- Zone-level routes ---

@zone_router.post("/sync")
async def sync_ssl(
    account_id: int,
    zone_id: str,
    x_cf_token: str = Header(..., alias="X-Cf-Token"),
):
    """Fetch SSL setting from Cloudflare API and sync to MongoDB"""
    account = await query_one("SELECT id, name FROM account WHERE id = %s", (account_id,))
    if not account:
        raise HTTPException(status_code=404, detail="Account not found")

    cf_data = cf_client.get_ssl(x_cf_token, zone_id)
    if not cf_data.get("success"):
        raise HTTPException(status_code=500, detail="Failed to fetch from Cloudflare")

    result = cf_data.get("result", {})
    now = datetime.utcnow()
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]

    doc = {
        "zone_id": zone_id,
        "account_id": str(account_id),
        "ssl_mode": result.get("value", "off"),
        "editable": result.get("editable", True),
        "modified_on": result.get("modified_on", ""),
        "synced_at": now,
    }
    await collection.update_one(
        {"zone_id": zone_id, "account_id": str(account_id)},
        {"$set": doc},
        upsert=True,
    )

    return {"code": 200, "data": {"synced": 1}}


@zone_router.get("")
async def list_ssl(account_id: int, zone_id: str):
    """Read SSL setting for a specific zone from MongoDB"""
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]
    rows = await collection.find({"account_id": str(account_id)}).to_list(length=100)
    for r in rows:
        r["_id"] = str(r["_id"])
    return {"code": 200, "data": rows}


@zone_router.patch("")
async def update_ssl(
    account_id: int,
    zone_id: str,
    x_cf_token: str = Header(..., alias="X-Cf-Token"),
    body: dict = None,
):
    """Update SSL mode via Cloudflare API and sync to MongoDB"""
    value = (body or {}).get("value", "full")
    cf_client.update_ssl(x_cf_token, zone_id, value)

    now = datetime.utcnow()
    db = await get_db()
    collection = db[get_collection_name(account_id, zone_id)]
    await collection.update_one(
        {"zone_id": zone_id, "account_id": str(account_id)},
        {"$set": {"ssl_mode": value, "synced_at": now}},
        upsert=True,
    )
    return {"code": 200, "data": {"ssl_mode": value}}
