from fastapi import APIRouter, HTTPException
import json
import logging

from app.services.db import execute, query_one, query_all

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE = "tool"


@router.get("/tools")
async def list_tools():
    rows = await query_all(f"SELECT * FROM `{TABLE}` ORDER BY created_at DESC")
    for row in rows:
        if isinstance(row.get("config"), str):
            try: row["config"] = json.loads(row["config"])
            except: row["config"] = {}
        row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": rows}


@router.post("/tools")
async def create_tool(body: dict):
    name = (body.get("name") or "").strip()
    if not name:
        raise HTTPException(status_code=400, detail="Name is required")
    description = (body.get("description") or "").strip()
    config = body.get("config") or {}
    enabled = int(body.get("enabled", True))
    await execute(
        f"INSERT INTO `{TABLE}` (name, description, config, enabled, created_at, updated_at) VALUES (%s, %s, %s, %s, UTC_TIMESTAMP(), UTC_TIMESTAMP())",
        (name, description, json.dumps(config), enabled),
    )
    row = await query_one(f"SELECT * FROM `{TABLE}` ORDER BY id DESC LIMIT 1")
    if row: row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": row, "message": "Tool created"}


@router.put("/tools/{item_id}")
async def update_tool(item_id: int, body: dict):
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (item_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Tool not found")
    name = (body.get("name") or row.get("name", "")).strip()
    description = (body.get("description") or row.get("description", "")).strip()
    config = body.get("config") if "config" in body else row.get("config", {})
    enabled = int(body.get("enabled", row.get("enabled", True)))
    if isinstance(config, str):
        try: config = json.loads(config)
        except: config = {}
    await execute(
        f"UPDATE `{TABLE}` SET name=%s, description=%s, config=%s, enabled=%s, updated_at=UTC_TIMESTAMP() WHERE id=%s",
        (name, description, json.dumps(config), enabled, item_id),
    )
    updated = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (item_id,))
    if updated: updated["enabled"] = bool(updated.get("enabled"))
    return {"code": 200, "data": updated, "message": "Tool updated"}


@router.delete("/tools/{item_id}")
async def delete_tool(item_id: int):
    count = await execute(f"DELETE FROM `{TABLE}` WHERE id = %s", (item_id,))
    if count == 0:
        raise HTTPException(status_code=404, detail="Tool not found")
    return {"code": 200, "message": "Tool deleted"}


@router.patch("/tools/{item_id}")
async def patch_tool(item_id: int, body: dict):
    return await update_tool(item_id, body)
