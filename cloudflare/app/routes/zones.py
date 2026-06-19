from fastapi import APIRouter, Header, HTTPException
from datetime import datetime
import logging

from app.services.db import query_one
from app.services.mongodb import get_db
from app.services import cf_client

logger = logging.getLogger(__name__)
router = APIRouter()


def get_collection_name(account_id: int, suffix: str) -> str:
    """Generate collection name based on account: account_{id}_{suffix}"""
    return f"account_{account_id}_{suffix}"


@router.post("/sync")
async def sync_zones(account_id: int, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    """Fetch zones from Cloudflare API and sync to MongoDB"""
    logger.info(f"[Zone Sync] Start sync for account_id={account_id}")
    account = await query_one("SELECT id, name, tags FROM account WHERE id = %s", (account_id,))
    if not account:
        raise HTTPException(status_code=404, detail="Account not found")

    db = await get_db()
    collection = db[get_collection_name(account_id, "zones")]

    try:
        cf_data = await cf_client.async_list_zones(x_cf_token)
    except Exception as e:
        logger.error(f"[Zone Sync] CF API error for account_id={account_id}: {type(e).__name__}: {e}")
        return {"code": 403, "message": f"CF API error: {type(e).__name__}"}
    if not cf_data.get("success"):
        logger.error(f"[Zone Sync] Failed to fetch zones from CF for account_id={account_id}")
        raise HTTPException(status_code=500, detail="Failed to fetch from Cloudflare")

    zones = cf_data.get("result", [])
    logger.info(f"[Zone Sync] Fetched {len(zones)} zones from CF")
    now = datetime.utcnow()

    # 先清理该 account 的旧 zones
    deleted = await collection.delete_many({"account_id": str(account_id)})
    logger.info(f"[Zone Sync] Cleared {deleted.deleted_count} old zones for account_id={account_id}")

    # 收集其他 account 的已有 zone_id，避免跨 account 重复
    existing_zones = set()
    db_cols = await db.list_collection_names()
    for col_name in db_cols:
        if col_name.endswith("_zones") and col_name != get_collection_name(account_id, "zones"):
            async for z in db[col_name].find({}, {"zone_id": 1}):
                existing_zones.add(z.get("zone_id"))
    logger.info(f"[Zone Sync] Found {len(existing_zones)} zones in other accounts")

    synced = 0
    skipped = 0
    for zone in zones:
        if zone["id"] in existing_zones:
            skipped += 1
            continue
        existing_zones.add(zone["id"])
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

    logger.info(f"[Zone Sync] Complete for account_id={account_id}: synced={synced}, skipped={skipped} (already in other account)")
    return {"code": 200, "data": {"synced": synced, "skipped": skipped, "total": len(zones)}}


@router.get("")
async def list_zones(account_id: int = None):
    """Read zones from MongoDB"""
    db = await get_db()

    if account_id:
        collection = db[get_collection_name(account_id, "zones")]
        query = {"account_id": str(account_id)}
        rows = await collection.find(query).sort("name", 1).to_list(length=5000)
    else:
        # Fetch from all accounts
        collections = await db.list_collection_names()
        rows = []
        for col_name in collections:
            if col_name.endswith("_zones"):
                col = db[col_name]
                docs = await col.find({}).sort("name", 1).to_list(length=5000)
                rows.extend(docs)

    for r in rows:
        r["_id"] = str(r["_id"])

    return {"code": 200, "data": rows}


@router.delete("/sync")
async def clear_zones(account_id: int = None):
    """Clear synced zone data from MongoDB"""
    db = await get_db()

    if account_id:
        collection = db[get_collection_name(account_id, "zones")]
        query = {"account_id": str(account_id)}
    else:
        raise HTTPException(status_code=400, detail="account_id is required")

    result = await collection.delete_many(query)
    return {"code": 200, "data": {"deleted": result.deleted_count}}
