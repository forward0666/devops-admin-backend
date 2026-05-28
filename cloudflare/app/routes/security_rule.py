from fastapi import APIRouter, HTTPException, Query
from datetime import datetime
from bson import ObjectId

from app.services.mongodb import get_db

router = APIRouter()

COLLECTION = "security_rules"


def _serialize(doc: dict) -> dict:
    doc["id"] = str(doc.pop("_id"))
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

    doc = {
        "projectId": project_id,
        "env": env,
        "name": name,
        "ruleId": (body.get("ruleId") or "").strip(),
        "zone": (body.get("zone") or "").strip(),
        "username": (body.get("username") or "").strip(),
        "userIp": (body.get("userIp") or "").strip(),
        "operator": (body.get("operator") or "").strip(),
        "domain": (body.get("domain") or "").strip(),
        "action": (body.get("action") or "allow").strip(),
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
    for field in ["name", "env", "ruleId", "zone", "username", "userIp", "operator", "domain", "action"]:
        if body.get(field) is not None:
            update_fields[field] = body[field].strip() if isinstance(body[field], str) else body[field]

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
