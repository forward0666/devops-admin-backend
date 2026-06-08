from fastapi import APIRouter, HTTPException
import json
import logging

from app.services.db import execute, query_one, query_all

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE = "agent"


@router.get("")
async def list_agents():
    """List all agents"""
    rows = await query_all(f"SELECT * FROM `{TABLE}` ORDER BY created_at DESC")
    for row in rows:
        if isinstance(row.get("config"), str):
            try:
                row["config"] = json.loads(row["config"])
            except Exception:
                row["config"] = {}
        row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": rows}


@router.post("")
async def create_agent(body: dict):
    """Create a new agent"""
    name = (body.get("name") or "").strip()
    agent_type = (body.get("type") or "").strip()
    mcp_url = (body.get("mcp_url") or "").strip()
    description = (body.get("description") or "").strip()
    config = body.get("config") or {}

    if not name:
        raise HTTPException(status_code=400, detail="Name is required")
    if not agent_type:
        raise HTTPException(status_code=400, detail="Type is required")

    await execute(
        f"INSERT INTO `{TABLE}` (name, type, mcp_url, description, config, enabled, status, created_at, updated_at) "
        f"VALUES (%s, %s, %s, %s, %s, %s, 'offline', UTC_TIMESTAMP(), UTC_TIMESTAMP())",
        (name, agent_type, mcp_url, description, json.dumps(config), int(body.get("enabled", True))),
    )
    row = await query_one(f"SELECT * FROM `{TABLE}` ORDER BY id DESC LIMIT 1")
    if row:
        row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": row, "message": "Agent created"}


@router.get("/{agent_id}")
async def get_agent(agent_id: int):
    """Get agent by ID"""
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Agent not found")
    if isinstance(row.get("config"), str):
        try:
            row["config"] = json.loads(row["config"])
        except Exception:
            row["config"] = {}
    row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": row}


@router.put("/{agent_id}")
async def update_agent(agent_id: int, body: dict):
    """Update an agent"""
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Agent not found")

    name = (body.get("name") or row.get("name", "")).strip()
    agent_type = (body.get("type") or row.get("type", "")).strip()
    mcp_url = (body.get("mcp_url") or row.get("mcp_url", "")).strip()
    description = (body.get("description") or row.get("description", "")).strip()
    config = body.get("config") if "config" in body else row.get("config", {})
    enabled = int(body.get("enabled", row.get("enabled", True)))

    if isinstance(config, str):
        try:
            config = json.loads(config)
        except Exception:
            config = {}

    await execute(
        f"UPDATE `{TABLE}` SET name=%s, type=%s, mcp_url=%s, description=%s, config=%s, enabled=%s, updated_at=UTC_TIMESTAMP() WHERE id=%s",
        (name, agent_type, mcp_url, description, json.dumps(config), enabled, agent_id),
    )
    updated = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if updated:
        updated["enabled"] = bool(updated.get("enabled"))
    return {"code": 200, "data": updated, "message": "Agent updated"}


@router.delete("/{agent_id}")
async def delete_agent(agent_id: int):
    """Delete an agent"""
    count = await execute(f"DELETE FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if count == 0:
        raise HTTPException(status_code=404, detail="Agent not found")
    return {"code": 200, "message": "Agent deleted"}


@router.patch("/{agent_id}")
async def patch_agent(agent_id: int, body: dict):
    """Partial update (e.g. toggle enabled)"""
    return await update_agent(agent_id, body)


@router.get("/{agent_id}/status")
async def check_agent_status(agent_id: int):
    """Check agent status from Nacos"""
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Agent not found")

    # Check Nacos for service status
    import httpx
    from app.config import NACOS_HOST, NACOS_PORT, NACOS_NAMESPACE, NACOS_USERNAME, NACOS_PASSWORD

    service_name = f"weather-agent"  # TODO: map agent type to service name
    status = "offline"
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(
                f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1/ns/instance/list",
                params={
                    "serviceName": service_name,
                    "namespaceId": NACOS_NAMESPACE,
                    "username": NACOS_USERNAME,
                    "password": NACOS_PASSWORD,
                },
            )
            if resp.status_code == 200:
                hosts = resp.json().get("hosts", [])
                if hosts and hosts[0].get("healthy"):
                    status = "online"
    except Exception:
        pass

    # Update status in DB
    await execute(f"UPDATE `{TABLE}` SET status=%s, last_active_at=UTC_TIMESTAMP() WHERE id=%s", (status, agent_id))

    return {"code": 200, "data": {"status": status}}
