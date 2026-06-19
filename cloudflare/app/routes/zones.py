from fastapi import APIRouter, Header, HTTPException
from datetime import datetime
import logging

from app.services.db import query_one, query_all
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

    # 1. 验证 token
    try:
        verify_data = await cf_client.async_verify_token(x_cf_token)
        if verify_data.get("success"):
            token_status = verify_data.get("result", {}).get("status", "unknown")
            logger.info(f"[Zone Sync] Token status: {token_status}")
        else:
            logger.warning(f"[Zone Sync] Token verify failed: {verify_data}")
    except Exception as e:
        logger.warning(f"[Zone Sync] Token verify error (non-fatal): {e}")

    # 2. 获取 zones
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

    # 3. 按 CF account.id 分组，确定当前 token 对应的 CF account
    cf_account_counts: dict[str, int] = {}
    for z in zones:
        cf_acc = (z.get("account") or {}).get("id", "")
        if cf_acc:
            cf_account_counts[cf_acc] = cf_account_counts.get(cf_acc, 0) + 1
    cf_account_id = max(cf_account_counts, key=cf_account_counts.get) if cf_account_counts else ""
    logger.info(f"[Zone Sync] CF account groups: {cf_account_counts}, primary: {cf_account_id}")

    # 4. 清理旧数据
    deleted = await collection.delete_many({"account_id": str(account_id)})
    logger.info(f"[Zone Sync] Cleared {deleted.deleted_count} old zones for account_id={account_id}")

    # 5. 写入属于当前 CF account 的 zones
    now = datetime.utcnow()
    synced = 0
    skipped = 0
    for zone in zones:
        zone_cf_account = (zone.get("account") or {}).get("id", "")
        if cf_account_id and zone_cf_account != cf_account_id:
            skipped += 1
            continue
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

    logger.info(f"[Zone Sync] Complete for account_id={account_id}: synced={synced}, skipped={skipped} (other CF account)")
    return {"code": 200, "data": {"synced": synced, "skipped": skipped, "total": len(zones)}}


@router.post("/syncAll")
async def sync_all_zones():
    """Sync zones for all accounts"""
    accounts = await query_all("SELECT id, name FROM account ORDER BY id")
    if not accounts:
        raise HTTPException(status_code=400, detail="No accounts found")

    results = []
    for acc in accounts:
        try:
            # Get token for this account
            acc_row = await query_one("SELECT api_key FROM account WHERE id = %s", (acc["id"],))
            if not acc_row:
                continue
            token = acc_row["api_key"]
            # Reuse sync_zones logic
            result = await sync_zones(acc["id"], token)
            results.append({"account_id": acc["id"], "account_name": acc["name"], "data": result.get("data", {})})
        except Exception as e:
            logger.error(f"[Zone Sync All] Failed for account {acc['id']}: {e}")
            results.append({"account_id": acc["id"], "account_name": acc["name"], "error": str(e)})

    return {"code": 200, "data": results}


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
