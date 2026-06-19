from fastapi import APIRouter, Header, HTTPException
from datetime import datetime
import logging

from app.services.db import query_one
from app.services.mongodb import get_db
from app.services import cf_client

logger = logging.getLogger(__name__)
router = APIRouter()


def get_collection_name(account_id: int, suffix: str) -> str:
    return f"account_{account_id}_{suffix}"


@router.post("/sync")
async def sync_dns(account_id: int, x_cf_token: str = Header(..., alias="X-Cf-Token")):
    """Fetch DNS records from Cloudflare API for all zones and sync to MongoDB"""
    logger.info(f"[DNS Sync] Start sync for account_id={account_id}")
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
    ).to_list(length=2000)

    if not zones:
        raise HTTPException(status_code=404, detail="No synced zones found for this account. Sync zones first.")

    import asyncio

    # 先清理该 account 的旧 DNS 记录
    deleted = await dns_collection.delete_many({"account_id": str(account_id)})
    logger.info(f"[DNS Sync] Cleared {deleted.deleted_count} old records for account_id={account_id}")

    now = datetime.utcnow()
    total_synced = 0
    logger.info(f"[DNS Sync] Found {len(zones)} zones for account_id={account_id}")

    async def sync_one_zone(zone):
        zone_id = zone["zone_id"]
        zone_name = zone["name"]
        count = 0
        try:
            cf_data = await cf_client.async_list_dns(x_cf_token, zone_id)
        except Exception as e:
            logger.error(f"[DNS Sync] Failed to fetch DNS for zone {zone_name} ({zone_id}): {e}")
            return 0

        if not cf_data.get("success"):
            logger.warning(f"[DNS Sync] CF API returned failure for zone {zone_name} ({zone_id})")
            return 0

        records = cf_data.get("result", [])
        logger.info(f"[DNS Sync] Zone {zone_name}: fetched {len(records)} records")

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
            count += 1
        return count

    results = await asyncio.gather(*[sync_one_zone(z) for z in zones])
    total_synced = sum(results)

    logger.info(f"[DNS Sync] Complete for account_id={account_id}: synced={total_synced}")
    return {"code": 200, "data": {"synced": total_synced}}


@router.get("")
async def list_dns(account_id: int = None):
    """Read DNS records from MongoDB"""
    db = await get_db()

    if account_id:
        collection = db[get_collection_name(account_id, "dns_records")]
        query = {"account_id": str(account_id)}
        rows = await collection.find(query).sort("name", 1).to_list(length=5000)
    else:
        # Fetch from all accounts
        db_list = await get_db()
        collections = await db_list.list_collection_names()
        rows = []
        for col_name in collections:
            if col_name.endswith("_dns_records"):
                col = db_list[col_name]
                docs = await col.find({}).sort("name", 1).to_list(length=5000)
                rows.extend(docs)

    for r in rows:
        r["_id"] = str(r["_id"])

    return {"code": 200, "data": rows}
