import asyncio
import logging
from datetime import datetime
from fastapi import APIRouter, HTTPException

from app.services.db import query_all, query_one, execute

logger = logging.getLogger(__name__)
router = APIRouter()


@router.get("")
async def list_rules():
    rows = await query_all(
        "SELECT id, name, domains, description, enabled, status, last_check, created_at, updated_at "
        "FROM monitor_rule ORDER BY id DESC"
    )
    for r in rows:
        if isinstance(r.get("domains"), str):
            try:
                import json
                r["domains"] = json.loads(r["domains"])
            except: r["domains"] = []
        elif r.get("domains") is None:
            r["domains"] = []
        r["enabled"] = bool(r.get("enabled", 0))
    return {"code": 200, "data": rows}


@router.get("/{rule_id}")
async def get_rule(rule_id: int):
    row = await query_one(
        "SELECT id, name, domains, description, enabled, status, last_check, created_at, updated_at "
        "FROM monitor_rule WHERE id = %s", (rule_id,)
    )
    if not row:
        raise HTTPException(status_code=404, detail="Rule not found")
    if isinstance(row.get("domains"), str):
        try:
            import json
            row["domains"] = json.loads(row["domains"])
        except: row["domains"] = []
    elif row.get("domains") is None:
        row["domains"] = []
    row["enabled"] = bool(row.get("enabled", 0))
    return {"code": 200, "data": row}


@router.post("")
async def create_rule(body: dict):
    name = body.get("name", "").strip()
    domains = body.get("domains", [])
    description = body.get("description", "").strip()
    enabled = body.get("enabled", True)

    if not name:
        raise HTTPException(status_code=400, detail="name is required")

    import json
    domains_json = json.dumps(domains)

    await execute(
        "INSERT INTO monitor_rule (name, domains, description, enabled) VALUES (%s, %s, %s, %s)",
        (name, domains_json, description, enabled),
    )
    logger.info(f"[Monitor] Created rule: {name} (domains={len(domains)})")
    return {"code": 200, "message": "ok"}


@router.put("/{rule_id}")
async def update_rule(rule_id: int, body: dict):
    existing = await query_one("SELECT id FROM monitor_rule WHERE id = %s", (rule_id,))
    if not existing:
        raise HTTPException(status_code=404, detail="Rule not found")

    name = body.get("name", "").strip()
    domains = body.get("domains", [])
    description = body.get("description", "").strip()
    enabled = body.get("enabled", True)

    if not name:
        raise HTTPException(status_code=400, detail="name is required")

    import json
    domains_json = json.dumps(domains)

    await execute(
        "UPDATE monitor_rule SET name=%s, domains=%s, description=%s, enabled=%s, updated_at=UTC_TIMESTAMP() WHERE id=%s",
        (name, domains_json, description, enabled, rule_id),
    )
    logger.info(f"[Monitor] Updated rule {rule_id}: {name} (domains={len(domains)})")
    return {"code": 200, "message": "ok"}


@router.delete("/{rule_id}")
async def delete_rule(rule_id: int):
    existing = await query_one("SELECT id FROM monitor_rule WHERE id = %s", (rule_id,))
    if not existing:
        raise HTTPException(status_code=404, detail="Rule not found")

    await execute("DELETE FROM monitor_rule WHERE id = %s", (rule_id,))
    logger.info(f"[Monitor] Deleted rule {rule_id}")
    return {"code": 200, "message": "ok"}


@router.post("/{rule_id}/toggle")
async def toggle_rule(rule_id: int):
    existing = await query_one("SELECT id, enabled FROM monitor_rule WHERE id = %s", (rule_id,))
    if not existing:
        raise HTTPException(status_code=404, detail="Rule not found")

    new_status = not existing["enabled"]
    await execute("UPDATE monitor_rule SET enabled=%s, updated_at=UTC_TIMESTAMP() WHERE id=%s", (new_status, rule_id))
    logger.info(f"[Monitor] Toggled rule {rule_id}: enabled={new_status}")
    return {"code": 200, "data": {"enabled": new_status}}


@router.post("/{rule_id}/check")
async def manual_check(rule_id: int):
    from app.services.checker import run_single_check
    rule = await query_one("SELECT id FROM monitor_rule WHERE id = %s", (rule_id,))
    if not rule:
        raise HTTPException(status_code=404, detail="Rule not found")

    asyncio.create_task(run_single_check(rule_id))
    logger.info(f"[Monitor] Manual check triggered for rule {rule_id}")
    return {"code": 200, "message": "Check triggered"}


@router.get("/{rule_id}/results")
async def get_results(rule_id: int, limit: int = 100):
    from app.services.mongodb import get_db
    rule = await query_one("SELECT id FROM monitor_rule WHERE id = %s", (rule_id,))
    if not rule:
        raise HTTPException(status_code=404, detail="Rule not found")

    db = await get_db()
    collection = db[f"monitor_results_{rule_id}"]
    rows = await collection.find({}).sort("checked_at", -1).limit(limit).to_list(length=limit)
    for r in rows:
        r["_id"] = str(r["_id"])
    return {"code": 200, "data": rows}


@router.get("/{rule_id}/results/latest")
async def get_latest_results(rule_id: int):
    from app.services.mongodb import get_db
    rule = await query_one("SELECT id FROM monitor_rule WHERE id = %s", (rule_id,))
    if not rule:
        raise HTTPException(status_code=404, detail="Rule not found")

    db = await get_db()
    collection = db[f"monitor_results_{rule_id}"]
    rows = await collection.find({}).sort("domain", 1).to_list(length=1000)
    for r in rows:
        r["_id"] = str(r["_id"])
    return {"code": 200, "data": rows}
