from fastapi import APIRouter, HTTPException
import json
import logging

from app.services.db import execute, query_one, query_all

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE = "mcp"


@router.get("/mcps")
async def list_mcps():
    rows = await query_all(f"SELECT * FROM `{TABLE}` ORDER BY created_at DESC")
    for row in rows:
        if isinstance(row.get("config"), str):
            try: row["config"] = json.loads(row["config"])
            except: row["config"] = {}
        row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": rows}


@router.post("/mcps")
async def create_mcp(body: dict):
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
    return {"code": 200, "data": row, "message": "MCP created"}


@router.put("/mcps/{item_id}")
async def update_mcp(item_id: int, body: dict):
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (item_id,))
    if not row:
        raise HTTPException(status_code=404, detail="MCP not found")
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
    return {"code": 200, "data": updated, "message": "MCP updated"}


@router.delete("/mcps/{item_id}")
async def delete_mcp(item_id: int):
    count = await execute(f"DELETE FROM `{TABLE}` WHERE id = %s", (item_id,))
    if count == 0:
        raise HTTPException(status_code=404, detail="MCP not found")
    return {"code": 200, "message": "MCP deleted"}


@router.patch("/mcps/{item_id}")
async def patch_mcp(item_id: int, body: dict):
    return await update_mcp(item_id, body)
