from fastapi import APIRouter, HTTPException
import json
import logging

from app.services.db import execute, query_one, query_all

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE = "ai_model"


@router.get("/models")
async def list_models():
    """List all AI models"""
    rows = await query_all(f"SELECT * FROM `{TABLE}` ORDER BY created_at DESC")
    for row in rows:
        # Mask API key for security
        if row.get("api_key"):
            key = row["api_key"]
            row["api_key_masked"] = key[:8] + "***" + key[-4:] if len(key) > 12 else "***"
        row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": rows}


@router.post("/models")
async def create_model(body: dict):
    """Create a new AI model"""
    name = (body.get("name") or "").strip()
    provider = (body.get("provider") or "").strip()
    model_id = (body.get("model_id") or "").strip()
    base_url = (body.get("base_url") or "").strip()
    api_key = (body.get("api_key") or "").strip()
    description = (body.get("description") or "").strip()

    if not name:
        raise HTTPException(status_code=400, detail="Name is required")
    if not provider:
        raise HTTPException(status_code=400, detail="Provider is required")
    if not model_id:
        raise HTTPException(status_code=400, detail="Model ID is required")

    await execute(
        f"INSERT INTO `{TABLE}` (name, provider, model_id, base_url, api_key, description, enabled, created_at, updated_at) "
        f"VALUES (%s, %s, %s, %s, %s, %s, %s, UTC_TIMESTAMP(), UTC_TIMESTAMP())",
        (name, provider, model_id, base_url, api_key, description, int(body.get("enabled", True))),
    )
    row = await query_one(f"SELECT * FROM `{TABLE}` ORDER BY id DESC LIMIT 1")
    if row:
        row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": row, "message": "Model created"}


@router.get("/models/{model_id}")
async def get_model(model_id: int):
    """Get model by ID"""
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (model_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Model not found")
    row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": row}


@router.put("/models/{model_id}")
async def update_model(model_id: int, body: dict):
    """Update a model"""
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (model_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Model not found")

    name = (body.get("name") or row.get("name", "")).strip()
    provider = (body.get("provider") or row.get("provider", "")).strip()
    model_id_val = (body.get("model_id") or row.get("model_id", "")).strip()
    base_url = (body.get("base_url") or row.get("base_url", "")).strip()
    api_key = (body.get("api_key") if "api_key" in body else row.get("api_key", "")).strip()
    description = (body.get("description") or row.get("description", "")).strip()
    enabled = int(body.get("enabled", row.get("enabled", True)))

    await execute(
        f"UPDATE `{TABLE}` SET name=%s, provider=%s, model_id=%s, base_url=%s, api_key=%s, description=%s, enabled=%s, updated_at=UTC_TIMESTAMP() WHERE id=%s",
        (name, provider, model_id_val, base_url, api_key, description, enabled, model_id),
    )
    updated = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (model_id,))
    if updated:
        updated["enabled"] = bool(updated.get("enabled"))
    return {"code": 200, "data": updated, "message": "Model updated"}


@router.delete("/models/{model_id}")
async def delete_model(model_id: int):
    """Delete a model"""
    count = await execute(f"DELETE FROM `{TABLE}` WHERE id = %s", (model_id,))
    if count == 0:
        raise HTTPException(status_code=404, detail="Model not found")
    return {"code": 200, "message": "Model deleted"}


@router.patch("/models/{model_id}")
async def patch_model(model_id: int, body: dict):
    """Partial update (e.g. toggle enabled)"""
    return await update_model(model_id, body)
