from fastapi import APIRouter, Header, HTTPException
from datetime import datetime

from app.services.db import query_one
from app.services.mongodb import get_db
from app.services import cf_client

router = APIRouter()


def get_collection_name(account_id: int, suffix: str) -> str:
    return f"account_{account_id}_{suffix}"


@router.post("/sync")
async def sync_dns(account_id: int, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    """Fetch DNS records from Cloudflare API for all zones and sync to MongoDB"""
    account = await query_one("SELECT id, name, tags FROM account WHERE id = %s", (account_id,))
    if not account:
        raise HTTPException(status_code=404, detail="Account not found")

    db = await get_db()
    zones_collection = db[get_collection_name(account_id, "zones")]
    dns_collection = db[get_collection_name(account_id, "dns_records")]

    # Get all zones for this account
    zones = await zones_collection.find(
        {"account_id": str(account_id)},
        {"zone_id": 1, "name": 1}
    ).to_list(length=200)

    if not zones:
        raise HTTPException(status_code=404, detail="No synced zones found for this account. Sync zones first.")

    now = datetime.utcnow()
    total_synced = 0

    for zone in zones:
        zone_id = zone["zone_id"]
        zone_name = zone["name"]

        try:
            cf_data = cf_client.list_dns(x_cf_token, zone_id)
        except Exception:
            continue

        if not cf_data.get("success"):
            continue

        records = cf_data.get("result", [])

        for r in records:
            doc = {
                "record_id": r["id"],
                "zone_id": zone_id,
                "zone_name": zone_name,
                "account_id": str(account_id),
                "account_name": account["name"],
                "type": r.get("type", ""),
                "name": r.get("name", ""),
                "content": r.get("content", ""),
                "proxied": r.get("proxied", False),
                "ttl": r.get("ttl", 1),
                "priority": r.get("priority"),
                "synced_at": now,
            }

            await dns_collection.update_one(
                {"record_id": r["id"], "account_id": str(account_id)},
                {"$set": doc},
                upsert=True,
            )
            total_synced += 1

    # Clean up stale records
    stale = await dns_collection.delete_many({
        "account_id": str(account_id),
        "synced_at": {"$lt": now},
    })

    return {"code": 200, "data": {"synced": total_synced, "stale_removed": stale.deleted_count}}


@router.get("")
async def list_dns(account_id: int = None):
    """Read DNS records from MongoDB"""
    db = await get_db()

    if not account_id:
        raise HTTPException(status_code=400, detail="account_id is required")

    collection = db[get_collection_name(account_id, "dns_records")]
    query = {"account_id": str(account_id)}

    rows = await collection.find(query).sort("name", 1).to_list(length=5000)
    for r in rows:
        r["_id"] = str(r["_id"])

    return {"code": 200, "data": rows}
