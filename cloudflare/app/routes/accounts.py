from fastapi import APIRouter, HTTPException
from app.services.db import query_all, query_one, execute

router = APIRouter()


@router.get("")
async def list_accounts():
    rows = await query_all("SELECT id, name, api_key, description, tags, status, created_at, updated_at FROM account ORDER BY id DESC")
    return {"code": 200, "data": rows}


@router.get("/{account_id}")
async def get_account(account_id: int):
    row = await query_one("SELECT id, name, api_key, description, tags, status, created_at, updated_at FROM account WHERE id = %s", (account_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Account not found")
    return {"code": 200, "data": row}


@router.post("")
async def create_account(body: dict):
    name = body.get("name", "").strip()
    api_key = body.get("apiKey", "").strip()
    description = body.get("description", "").strip()
    tags = ",".join(body.get("tags", []))

    if not name or not api_key:
        raise HTTPException(status_code=400, detail="name and apiKey are required")

    await execute(
        "INSERT INTO account (name, api_key, description, tags) VALUES (%s, %s, %s, %s)",
        (name, api_key, description, tags),
    )
    return {"code": 200, "message": "ok"}


@router.put("/{account_id}")
async def update_account(account_id: int, body: dict):
    existing = await query_one("SELECT id FROM account WHERE id = %s", (account_id,))
    if not existing:
        raise HTTPException(status_code=404, detail="Account not found")

    name = body.get("name", "").strip()
    api_key = body.get("apiKey", "").strip()
    description = body.get("description", "").strip()
    tags = ",".join(body.get("tags", []))

    if not name:
        raise HTTPException(status_code=400, detail="name is required")

    if api_key:
        await execute(
            "UPDATE account SET name = %s, api_key = %s, description = %s, tags = %s WHERE id = %s",
            (name, api_key, description, tags, account_id),
        )
    else:
        await execute(
            "UPDATE account SET name = %s, description = %s, tags = %s WHERE id = %s",
            (name, description, tags, account_id),
        )
    return {"code": 200, "message": "ok"}


@router.delete("/{account_id}")
async def delete_account(account_id: int):
    existing = await query_one("SELECT id FROM account WHERE id = %s", (account_id,))
    if not existing:
        raise HTTPException(status_code=404, detail="Account not found")

    await execute("DELETE FROM account WHERE id = %s", (account_id,))
    return {"code": 200, "message": "ok"}


@router.get("/{account_id}/key")
async def get_account_key(account_id: int):
    """Return masked API key for display"""
    row = await query_one("SELECT api_key FROM account WHERE id = %s", (account_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Account not found")
    key = row["api_key"]
    masked = key[:4] + "••••••••" + key[-4:] if len(key) > 8 else "••••••••"
    return {"code": 200, "data": {"maskedKey": masked}}


@router.get("/{account_id}/token")
async def get_account_token(account_id: int):
    """Return full API token for proxy calls"""
    row = await query_one("SELECT api_key FROM account WHERE id = %s", (account_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Account not found")
    return {"code": 200, "data": {"token": row["api_key"]}}
