from fastapi import APIRouter, HTTPException
from datetime import datetime
import logging

from app.services.db import query_all
from app.services.mongodb import get_db

logger = logging.getLogger(__name__)

router = APIRouter()

COLLECTION = "dns_domains"


@router.post("/sync")
async def sync_dns_domains():
    """从所有 account 的 dns_records 同步 A/CNAME 记录到 dns_domains 集合"""
    db = await get_db()
    accounts = await query_all("SELECT id, name FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No accounts found")

    now = datetime.utcnow()
    total_synced = 0

    # 清空旧数据
    await db[COLLECTION].delete_many({})

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
            await db[COLLECTION].insert_many(docs)
            total_synced += len(docs)

    # 建索引
    await db[COLLECTION].create_index("name")
    await db[COLLECTION].create_index("type")
    await db[COLLECTION].create_index([("account_id", 1), ("type", 1)])
    await db[COLLECTION].create_index("zone_name")

    logger.info(f"DNS domains sync complete: {total_synced} records from {len(accounts)} accounts")

    return {"code": 200, "data": {"synced": total_synced, "accounts": len(accounts)}}


@router.get("")
async def list_dns_domains(
    account_id: int = None,
    type: str = None,
    keyword: str = None,
):
    """查询 dns_domains 集合"""
    db = await get_db()
    query = {}
    if account_id:
        query["account_id"] = str(account_id)
    if type:
        query["type"] = type
    if keyword:
        query["$or"] = [
            {"name": {"$regex": keyword, "$options": "i"}},
            {"content": {"$regex": keyword, "$options": "i"}},
        ]

    rows = await db[COLLECTION].find(query).sort("name", 1).to_list(length=5000)

    for r in rows:
        r["id"] = str(r.pop("_id"))

    return {"code": 200, "data": rows}
