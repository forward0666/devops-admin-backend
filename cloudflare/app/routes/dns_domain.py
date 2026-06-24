from fastapi import APIRouter, HTTPException
from datetime import datetime
import logging

from app.services.db import query_all
from app.services.mongodb import get_db

logger = logging.getLogger(__name__)

router = APIRouter()

COLLECTION = "dns_domain"


@router.post("/sync")
async def sync_dns_domains():
    """从所有 account 的 dns_records 同步 A/CNAME 记录到 dns_domain 集合"""
    db = await get_db()
    accounts = await query_all("SELECT id, name FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No accounts found")

    now = datetime.utcnow()
    total_synced = 0

    for acc in accounts:
        acc_id = acc["id"]
        coll_name = f"account_{acc_id}_dns_records"
        coll = db[coll_name]

        # 先看看该集合总共有多少 A 和 CNAME 记录
        all_a = await coll.count_documents({"account_id": str(acc_id), "type": "A"})
        all_cname = await coll.count_documents({"account_id": str(acc_id), "type": "CNAME"})
        logger.info(f"Account {acc_id} ({acc['name']}): {all_a} A records, {all_cname} CNAME records")

        # 查询 A 和 CNAME 记录
        records = await coll.find(
            {"account_id": str(acc_id), "type": {"$in": ["A", "CNAME"]}},
            {"record_id": 1, "zone_id": 1, "zone_name": 1, "account_id": 1,
             "account_name": 1, "type": 1, "name": 1, "content": 1,
             "proxied": 1, "ttl": 1, "priority": 1}
        ).to_list(length=10000)

        logger.info(f"Account {acc_id}: fetched {len(records)} A/CNAME records")

        if not records:
            continue

        docs = []
        for r in records:
            docs.append({
                "record_id": r.get("record_id", ""),
                "zone_id": r.get("zone_id", ""),
                "zone_name": r.get("zone_name", ""),
                "account_id": str(acc_id),
                "account_name": acc.get("name", ""),
                "type": r.get("type", ""),
                "name": r.get("name", ""),
                "content": r.get("content", ""),
                "proxied": r.get("proxied", False),
                "ttl": r.get("ttl", 1),
                "priority": r.get("priority"),
                "synced_at": now,
            })

        if docs:
            for doc in docs:
                await db[COLLECTION].update_one(
                    {"record_id": doc["record_id"]},
                    {"$set": doc, "$setOnInsert": {"is_public": False, "is_ignored": False}},
                    upsert=True,
                )
            total_synced += len(docs)

    # 删除本次同步未更新的过期记录
    stale = await db[COLLECTION].delete_many({"synced_at": {"$lt": now}})
    logger.info(f"DNS domains stale removed: {stale.deleted_count}")

    # 建索引
    await db[COLLECTION].create_index("name")
    await db[COLLECTION].create_index("type")
    await db[COLLECTION].create_index([("account_id", 1), ("type", 1)])
    await db[COLLECTION].create_index("zone_name")

    # 确保所有记录都有 is_public 和 is_ignored 字段
    await db[COLLECTION].update_many({"is_public": {"$exists": False}}, {"$set": {"is_public": False}})
    await db[COLLECTION].update_many({"is_ignored": {"$exists": False}}, {"$set": {"is_ignored": False}})

    logger.info(f"DNS domains sync complete: {total_synced} records from {len(accounts)} accounts")

    return {"code": 200, "data": {"synced": total_synced, "accounts": len(accounts)}}


@router.get("")
async def list_dns_domains(
    account_id: int = None,
    type: str = None,
    keyword: str = None,
    is_ignored: bool = None,
):
    """查询 dns_domain 集合"""
    db = await get_db()
    query = {}
    if account_id:
        query["account_id"] = str(account_id)
    if type:
        query["type"] = type
    if is_ignored is not None:
        query["is_ignored"] = is_ignored
    if keyword:
        query["$or"] = [
            {"name": {"$regex": keyword, "$options": "i"}},
            {"content": {"$regex": keyword, "$options": "i"}},
        ]

    rows = await db[COLLECTION].find(query).sort("name", 1).to_list(length=5000)

    for r in rows:
        r["id"] = str(r.pop("_id"))

    return {"code": 200, "data": rows}


@router.put("/toggleAll")
async def toggle_all_public(body: dict):
    """批量更新所有域名的 is_public 字段"""
    db = await get_db()
    is_public = body.get("is_public", False)
    result = await db[COLLECTION].update_many({}, {"$set": {"is_public": is_public}})
    logger.info(f"Toggled all dns_domain is_public={is_public}, matched={result.matched_count}")
    return {"code": 200, "data": {"updated": result.matched_count}}


@router.put("/{record_id}")
async def update_dns_domain(record_id: str, body: dict):
    """更新 dns_domain 记录（如 is_public 字段）"""
    from bson import ObjectId
    db = await get_db()

    try:
        oid = ObjectId(record_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid record ID")

    update_fields = {}
    if "is_public" in body:
        update_fields["is_public"] = body["is_public"]
    if "is_ignored" in body:
        update_fields["is_ignored"] = body["is_ignored"]
    if "remark" in body:
        update_fields["remark"] = body["remark"]

    if not update_fields:
        raise HTTPException(status_code=400, detail="No fields to update")

    result = await db[COLLECTION].update_one({"_id": oid}, {"$set": update_fields})
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="Record not found")

    logger.info(f"Updated dns_domain {record_id}: {update_fields}")
    return {"code": 200, "message": "ok"}
