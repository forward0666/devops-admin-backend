from fastapi import APIRouter, HTTPException, Query
from datetime import datetime
from bson import ObjectId
import logging

from app.services.mongodb import get_db, get_source_db

logger = logging.getLogger(__name__)

router = APIRouter()

GROUPS_COLLECTION = "domain_group"
META_COLLECTION = "domain_meta"


def _serialize(doc: dict) -> dict:
    doc["id"] = str(doc.pop("_id"))
    return doc


# ==================== Zones ====================

@router.get("/zones")
async def list_zones():
    """Read zones from cloudflare MongoDB (account_X_zones collections)."""
    db = await get_source_db()
    all_zones = []
    cols = await db.list_collection_names()
    for col_name in cols:
        if col_name.endswith("_zones"):
            account_id = col_name.replace("account_", "").replace("_zones", "")
            async for zone in db[col_name].find({}, {"_id": 0, "zone_id": 1, "name": 1, "status": 1, "account_id": 1}):
                if zone.get("zone_id") and zone.get("name"):
                    zone["accountName"] = f"Account {account_id}"
                    all_zones.append(zone)
    all_zones.sort(key=lambda x: x.get("name", ""))
    return {"code": 200, "data": all_zones}


# ==================== Groups ====================

@router.get("/groups")
async def list_groups():
    db = await get_db()
    rows = await db[GROUPS_COLLECTION].find({}).sort("name", 1).to_list(length=500)
    return {"code": 200, "data": [_serialize(r) for r in rows]}


@router.post("/groups")
async def create_group(body: dict):
    name = (body.get("name") or "").strip()
    if not name:
        raise HTTPException(status_code=400, detail="name is required")

    db = await get_db()
    existing = await db[GROUPS_COLLECTION].find_one({"name": name})
    if existing:
        raise HTTPException(status_code=400, detail=f"Group already exists: {name}")

    doc = {"name": name, "createdAt": datetime.utcnow(), "updatedAt": datetime.utcnow()}
    result = await db[GROUPS_COLLECTION].insert_one(doc)
    doc["_id"] = result.inserted_id
    return {"code": 200, "data": _serialize(doc), "message": "Group created"}


@router.put("/groups/{group_id}")
async def update_group(group_id: str, body: dict):
    name = (body.get("name") or "").strip()
    if not name:
        raise HTTPException(status_code=400, detail="name is required")

    db = await get_db()
    try:
        oid = ObjectId(group_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid group id")

    existing = await db[GROUPS_COLLECTION].find_one({"_id": oid})
    if not existing:
        raise HTTPException(status_code=404, detail="Group not found")

    dup = await db[GROUPS_COLLECTION].find_one({"name": name, "_id": {"$ne": oid}})
    if dup:
        raise HTTPException(status_code=400, detail=f"Group name already exists: {name}")

    await db[GROUPS_COLLECTION].update_one({"_id": oid}, {"$set": {"name": name, "updatedAt": datetime.utcnow()}})
    updated = await db[GROUPS_COLLECTION].find_one({"_id": oid})
    return {"code": 200, "data": _serialize(updated), "message": "Group updated"}


@router.delete("/groups/{group_id}")
async def delete_group(group_id: str):
    db = await get_db()
    try:
        oid = ObjectId(group_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid group id")

    existing = await db[GROUPS_COLLECTION].find_one({"_id": oid})
    if not existing:
        raise HTTPException(status_code=404, detail="Group not found")

    # Remove group assignments
    await db[META_COLLECTION].update_many({"groupId": group_id}, {"$unset": {"groupId": ""}})
    await db[GROUPS_COLLECTION].delete_one({"_id": oid})
    return {"code": 200, "data": None, "message": "Group deleted"}


# ==================== Domain Meta ====================

@router.get("/meta")
async def list_meta():
    """List all domain metadata (type, remark, groupId)."""
    db = await get_db()
    rows = await db[META_COLLECTION].find({}).to_list(length=5000)
    return {"code": 200, "data": [_serialize(r) for r in rows]}


@router.get("/meta/{zone_id}")
async def get_meta(zone_id: str):
    db = await get_db()
    doc = await db[META_COLLECTION].find_one({"zoneId": zone_id})
    if not doc:
        return {"code": 200, "data": None}
    return {"code": 200, "data": _serialize(doc)}


@router.post("/meta")
async def upsert_meta(body: dict):
    """Create or update domain metadata."""
    zone_id = body.get("zoneId")
    if not zone_id:
        raise HTTPException(status_code=400, detail="zoneId is required")

    db = await get_db()
    update_fields = {"updatedAt": datetime.utcnow()}
    if body.get("type") is not None:
        update_fields["type"] = body["type"]
    if body.get("remark") is not None:
        update_fields["remark"] = body["remark"]
    if body.get("groupId") is not None:
        update_fields["groupId"] = body["groupId"]
    if body.get("name") is not None:
        update_fields["name"] = body["name"]
    if body.get("source") is not None:
        update_fields["source"] = body["source"]

    result = await db[META_COLLECTION].update_one(
        {"zoneId": zone_id},
        {"$set": update_fields, "$setOnInsert": {"createdAt": datetime.utcnow(), "zoneId": zone_id}},
        upsert=True,
    )
    doc = await db[META_COLLECTION].find_one({"zoneId": zone_id})
    return {"code": 200, "data": _serialize(doc), "message": "Meta saved"}


@router.post("/meta/batch")
async def batch_upsert_meta(body: dict):
    """Batch update metadata for multiple zones."""
    items = body.get("items", [])
    if not items:
        raise HTTPException(status_code=400, detail="items is required")

    db = await get_db()
    count = 0
    for item in items:
        zone_id = item.get("zoneId")
        if not zone_id:
            continue
        update_fields = {"updatedAt": datetime.utcnow()}
        if item.get("type") is not None:
            update_fields["type"] = item["type"]
        if item.get("remark") is not None:
            update_fields["remark"] = item["remark"]
        if item.get("groupId") is not None:
            update_fields["groupId"] = item["groupId"]
        if item.get("name") is not None:
            update_fields["name"] = item["name"]
        if item.get("source") is not None:
            update_fields["source"] = item["source"]

        await db[META_COLLECTION].update_one(
            {"zoneId": zone_id},
            {"$set": update_fields, "$setOnInsert": {"createdAt": datetime.utcnow(), "zoneId": zone_id}},
            upsert=True,
        )
        count += 1

    return {"code": 200, "data": {"updated": count}, "message": f"Batch updated {count} items"}


@router.delete("/meta/{zone_id}")
async def delete_meta(zone_id: str):
    db = await get_db()
    await db[META_COLLECTION].delete_one({"zoneId": zone_id})
    return {"code": 200, "data": None, "message": "Meta deleted"}
