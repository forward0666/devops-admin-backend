from fastapi import APIRouter, HTTPException, Query
from datetime import datetime
from bson import ObjectId

from app.services.mongodb import get_db

router = APIRouter()

COLLECTION = "security_rules"


def _serialize(doc: dict) -> dict:
    doc["id"] = str(doc.pop("_id"))
    for k in ["ruleId", "zone", "zones", "ruleIds", "zoneIds"]:
        doc.pop(k, None)
    return doc


@router.get("")
async def list_security_rules(projectId: int = Query(...), env: str = Query(None)):
    db = await get_db()
    query = {"projectId": projectId}
    if env:
        query["env"] = {"$regex": f"^{env}$", "$options": "i"}
    rows = await db[COLLECTION].find(query).sort("createdAt", -1).to_list(length=500)
    return {"code": 200, "data": [_serialize(r) for r in rows]}


@router.post("")
async def create_security_rule(body: dict):
    project_id = body.get("projectId")
    name = (body.get("name") or "").strip()
    env = (body.get("env") or "").strip()

    if not project_id or not name:
        raise HTTPException(status_code=400, detail="projectId and name are required")

    db = await get_db()

    entries = body.get("entries") or []
    norm_entries = []
    for e in entries:
        if isinstance(e, dict):
            norm_entries.append({
                "zone": (e.get("zone") or "").strip(),
                "zoneId": (e.get("zoneId") or "").strip(),
                "ruleId": (e.get("ruleId") or "").strip(),
            })

    if not norm_entries:
        def _normalize_list(val):
            if isinstance(val, list):
                return [v.strip() for v in val if v and v.strip()]
            if isinstance(val, str) and val.strip():
                return [v.strip() for v in val.split(',') if v.strip()]
            return []
        zones = _normalize_list(body.get("zone") or body.get("zones"))
        rule_ids = _normalize_list(body.get("ruleId") or body.get("ruleIds"))
        zone_ids = _normalize_list(body.get("zoneId") or body.get("zoneIds"))
        max_len = max(len(zones), len(rule_ids), len(zone_ids), 1)
        for i in range(max_len):
            norm_entries.append({
                "zone": zones[i] if i < len(zones) else "",
                "zoneId": zone_ids[i] if i < len(zone_ids) else "",
                "ruleId": rule_ids[i] if i < len(rule_ids) else "",
            })

    seen = set()
    dedup_entries = []
    for e in norm_entries:
        key = (e["zone"], e["zoneId"], e["ruleId"])
        if key not in seen:
            seen.add(key)
            dedup_entries.append(e)
    norm_entries = dedup_entries

    new_rule_ids = {e["ruleId"] for e in norm_entries if e.get("ruleId")}
    if new_rule_ids:
        existing = await db[COLLECTION].find_one({
            "projectId": project_id,
            "entries.ruleId": {"$in": list(new_rule_ids)}
        })
        if existing:
            raise HTTPException(status_code=400, detail=f"Duplicate Rule ID: {', '.join(new_rule_ids)}")

    doc = {
        "projectId": project_id,
        "env": env,
        "name": name,
        "entries": norm_entries,
        "username": (body.get("username") or "").strip(),
        "userIp": (body.get("userIp") or "").strip(),
        "operator": (body.get("operator") or "").strip(),
        "createdAt": datetime.utcnow(),
        "updatedAt": datetime.utcnow(),
    }
    result = await db[COLLECTION].insert_one(doc)
    doc["_id"] = result.inserted_id
    return {"code": 200, "data": _serialize(doc), "message": "Rule created"}


@router.put("/{rule_id}")
async def update_security_rule(rule_id: str, body: dict):
    project_id = body.get("projectId")
    if not project_id:
        raise HTTPException(status_code=400, detail="projectId is required")

    db = await get_db()
    try:
        oid = ObjectId(rule_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid rule id")

    existing = await db[COLLECTION].find_one({"_id": oid, "projectId": project_id})
    if not existing:
        raise HTTPException(status_code=404, detail="Rule not found")

    update_fields = {"updatedAt": datetime.utcnow()}
    for field in ["name", "env", "username", "userIp", "operator"]:
        if body.get(field) is not None:
            update_fields[field] = body[field].strip() if isinstance(body[field], str) else body[field]

    if body.get("entries") is not None:
        entries = body["entries"]
        norm_entries = []
        for e in entries:
            if isinstance(e, dict):
                norm_entries.append({
                    "zone": (e.get("zone") or "").strip(),
                    "zoneId": (e.get("zoneId") or "").strip(),
                    "ruleId": (e.get("ruleId") or "").strip(),
                })
        seen = set()
        dedup = []
        for e in norm_entries:
            key = (e["zone"], e["zoneId"], e["ruleId"])
            if key not in seen:
                seen.add(key)
                dedup.append(e)
        update_fields["entries"] = dedup

    new_rule_ids = {e["ruleId"] for e in update_fields.get("entries", []) if e.get("ruleId")}
    if new_rule_ids:
        dup = await db[COLLECTION].find_one({
            "projectId": project_id,
            "_id": {"$ne": oid},
            "entries.ruleId": {"$in": list(new_rule_ids)}
        })
        if dup:
            raise HTTPException(status_code=400, detail=f"Duplicate Rule ID: {', '.join(new_rule_ids)}")

    await db[COLLECTION].update_one({"_id": oid}, {"$set": update_fields})
    updated = await db[COLLECTION].find_one({"_id": oid})
    return {"code": 200, "data": _serialize(updated), "message": "Rule updated"}


@router.delete("/{rule_id}")
async def delete_security_rule(rule_id: str, projectId: int = Query(...)):
    db = await get_db()
    try:
        oid = ObjectId(rule_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid rule id")

    existing = await db[COLLECTION].find_one({"_id": oid, "projectId": projectId})
    if not existing:
        raise HTTPException(status_code=404, detail="Rule not found")

    await db[COLLECTION].delete_one({"_id": oid})
    return {"code": 200, "data": None, "message": "Rule deleted"}
