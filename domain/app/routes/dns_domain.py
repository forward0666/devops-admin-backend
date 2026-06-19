from fastapi import APIRouter, HTTPException
from datetime import datetime
import logging

from app.services.mongodb import get_db, get_source_db

logger = logging.getLogger(__name__)

router = APIRouter()

COLLECTION = "domain"


@router.post("/sync")
async def sync_dns_domains():
    """从 cloudflare 库的所有 account_{id}_dns_records 集合同步 A/CNAME 记录"""
    db = await get_db()
    source_db = await get_source_db()
    logger.info(f"[Sync] Writing to db={db.name}, collection={COLLECTION}")
    logger.info(f"[Sync] Reading from source_db={source_db.name}")
    now = datetime.utcnow()
    total_synced = 0

    # 列出源库中所有 *_dns_records 集合
    collections = await source_db.list_collection_names()
    dns_collections = [c for c in collections if c.endswith("_dns_records")]

    if not dns_collections:
        raise HTTPException(status_code=400, detail="No dns_records collections found")

    for coll_name in dns_collections:
        coll = source_db[coll_name]
        records = await coll.find(
            {"type": {"$in": ["A", "CNAME"]}},
            {"record_id": 1, "zone_id": 1, "zone_name": 1, "account_id": 1,
             "account_name": 1, "type": 1, "name": 1, "content": 1,
             "proxied": 1, "ttl": 1, "priority": 1}
        ).to_list(length=10000)

        if not records:
            continue

        for r in records:
            doc = {
                "record_id": r.get("record_id", ""),
                "zone_id": r.get("zone_id", ""),
                "zone_name": r.get("zone_name", ""),
                "account_id": r.get("account_id", ""),
                "account_name": r.get("account_name", ""),
                "type": r.get("type", ""),
                "name": r.get("name", ""),
                "content": r.get("content", ""),
                "proxied": r.get("proxied", False),
                "ttl": r.get("ttl", 1),
                "priority": r.get("priority"),
                "synced_at": now,
            }
            sync_fields = {
                "record_id": doc["record_id"],
                "zone_id": doc["zone_id"],
                "zone_name": doc["zone_name"],
                "account_id": doc["account_id"],
                "account_name": doc["account_name"],
                "type": doc["type"],
                "name": doc["name"],
                "content": doc["content"],
                "proxied": doc["proxied"],
                "ttl": doc["ttl"],
                "priority": doc["priority"],
                "synced_at": now,
            }
            await db[COLLECTION].update_one(
                {"record_id": doc["record_id"]},
                {"$set": sync_fields, "$setOnInsert": {"is_public": False, "is_ignored": False, "remark": ""}},
                upsert=True,
            )
            total_synced += 1

    # 删除过期记录
    stale = await db[COLLECTION].delete_many({"synced_at": {"$lt": now}})
    logger.info(f"DNS domains stale removed: {stale.deleted_count}")

    # 建索引
    await db[COLLECTION].create_index("name")
    await db[COLLECTION].create_index("type")
    await db[COLLECTION].create_index("account_id")
    await db[COLLECTION].create_index("zone_name")

    # 确保字段存在
    await db[COLLECTION].update_many({"is_public": {"$exists": False}}, {"$set": {"is_public": False}})
    await db[COLLECTION].update_many({"is_ignored": {"$exists": False}}, {"$set": {"is_ignored": False}})
    await db[COLLECTION].update_many({"remark": {"$exists": False}}, {"$set": {"remark": ""}})

    logger.info(f"DNS domains sync complete: {total_synced} records from {len(dns_collections)} collections, target={db.name}.{COLLECTION}")
    return {"code": 200, "data": {"synced": total_synced, "collections": len(dns_collections)}}


@router.get("")
async def list_dns_domains(keyword: str = None, type: str = None, is_ignored: bool = None):
    """查询 dns_domains"""
    db = await get_db()
    query = {}
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
    """批量更新 is_public"""
    db = await get_db()
    is_public = body.get("is_public", False)
    result = await db[COLLECTION].update_many({}, {"$set": {"is_public": is_public}})
    return {"code": 200, "data": {"updated": result.matched_count}}


@router.put("/{record_id}")
async def update_dns_domain(record_id: str, body: dict):
    """更新单条记录"""
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

    return {"code": 200, "message": "ok"}
