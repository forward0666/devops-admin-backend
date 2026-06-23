import json
import logging
from fastapi import APIRouter, HTTPException

from app.services.db import query_all, query_one, execute

logger = logging.getLogger(__name__)
router = APIRouter()


@router.get("/rules")
async def list_rules():
    rows = await query_all(
        "SELECT id, name, group_id, project_id, env, type, description, enabled, status, last_check, created_at, updated_at "
        "FROM sync_domain_rule ORDER BY id DESC"
    )
    for r in rows:
        r["enabled"] = bool(r.get("enabled", 0))
    return {"code": 200, "data": rows}


@router.get("/rules/{rule_id}")
async def get_rule(rule_id: int):
    row = await query_one("SELECT * FROM sync_domain_rule WHERE id = %s", (rule_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Rule not found")
    row["enabled"] = bool(row.get("enabled", 0))
    return {"code": 200, "data": row}


@router.post("/rules")
async def create_rule(body: dict):
    name = body.get("name", "").strip()
    group_id = body.get("group_id", "")
    project_id = body.get("project_id", "")
    env = body.get("env", "prod")
    rtype = body.get("type", "web")
    description = body.get("description", "")
    enabled = 1 if body.get("enabled", True) else 0

    if not name:
        raise HTTPException(status_code=400, detail="Name is required")
    if not group_id or not project_id:
        raise HTTPException(status_code=400, detail="group_id and project_id are required")

    await execute(
        "INSERT INTO sync_domain_rule (name, group_id, project_id, env, type, description, enabled) VALUES (%s, %s, %s, %s, %s, %s, %s)",
        (name, group_id, project_id, env, rtype, description, enabled),
    )
    return {"code": 200, "message": "Rule created"}


@router.put("/rules/{rule_id}")
async def update_rule(rule_id: int, body: dict):
    existing = await query_one("SELECT id FROM sync_domain_rule WHERE id = %s", (rule_id,))
    if not existing:
        raise HTTPException(status_code=404, detail="Rule not found")

    name = body.get("name", "").strip()
    group_id = body.get("group_id", "")
    project_id = body.get("project_id", "")
    env = body.get("env", "prod")
    rtype = body.get("type", "web")
    description = body.get("description", "")
    enabled = 1 if body.get("enabled", True) else 0

    if not name:
        raise HTTPException(status_code=400, detail="Name is required")

    await execute(
        "UPDATE sync_domain_rule SET name=%s, group_id=%s, project_id=%s, env=%s, type=%s, description=%s, enabled=%s, updated_at=UTC_TIMESTAMP() WHERE id=%s",
        (name, group_id, project_id, env, rtype, description, enabled, rule_id),
    )
    return {"code": 200, "message": "Rule updated"}


@router.delete("/rules/{rule_id}")
async def delete_rule(rule_id: int):
    existing = await query_one("SELECT id FROM sync_domain_rule WHERE id = %s", (rule_id,))
    if not existing:
        raise HTTPException(status_code=404, detail="Rule not found")
    await execute("DELETE FROM sync_domain_rule WHERE id = %s", (rule_id,))
    return {"code": 200, "message": "Rule deleted"}


@router.post("/rules/{rule_id}/check")
async def check_rule(rule_id: int):
    """Execute sync: fetch domains from group, import to project."""
    rule = await query_one("SELECT * FROM sync_domain_rule WHERE id = %s", (rule_id,))
    if not rule:
        raise HTTPException(status_code=404, detail="Rule not found")

    group_id = rule.get("group_id")
    project_id = rule.get("project_id")
    env = rule.get("env", "prod")
    rtype = rule.get("type", "web")

    if not group_id or not project_id:
        await execute("UPDATE sync_domain_rule SET status='error', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
        raise HTTPException(status_code=400, detail="Rule missing group_id or project_id")

    try:
        import httpx
        # 1. Get group zones from domain service
        domain_url = "http://localhost:8084"  # domain service
        async with httpx.AsyncClient(timeout=30) as client:
            # Get groups
            resp = await client.get(f"{domain_url}/domain/groups")
            groups = resp.json().get("data", [])
            group = next((g for g in groups if g.get("id") == group_id), None)
            if not group:
                await execute("UPDATE sync_domain_rule SET status='error', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
                raise HTTPException(status_code=404, detail=f"Group '{group_id}' not found")

            # Get meta
            resp = await client.get(f"{domain_url}/domain/meta")
            meta_list = resp.json().get("data", [])
            group_zone_ids = [m.get("zoneId") for m in meta_list if m.get("groupId") == group_id]

            if not group_zone_ids:
                await execute("UPDATE sync_domain_rule SET status='ok', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
                return {"code": 200, "data": {"synced": 0, "message": "No zones in group"}}

            # Get zone names from cloudflare
            cf_url = "http://localhost:8090"
            zone_names = []
            for zone_id in group_zone_ids:
                try:
                    resp = await client.get(f"{cf_url}/zones", params={"zone_id": zone_id})
                    zones_data = resp.json().get("data", [])
                    for z in zones_data:
                        zone_names.append(z.get("name", ""))
                except Exception:
                    pass

            if not zone_names:
                await execute("UPDATE sync_domain_rule SET status='ok', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
                return {"code": 200, "data": {"synced": 0, "message": "No zone names found"}}

            # Import domains to project via user service
            user_url = "http://localhost:8084"  # user service
            domains = [{"domain": zn, "env": env, "type": rtype, "remark": "", "cdn": ""} for zn in zone_names if zn]
            resp = await client.post(f"{user_url}/domain/import", json={"projectId": project_id, "domains": domains})
            result = resp.json() if resp.status_code == 200 else {}

            synced = result.get("data", {}).get("synced", len(domains)) if isinstance(result.get("data"), dict) else len(domains)

            await execute("UPDATE sync_domain_rule SET status='ok', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
            return {"code": 200, "data": {"synced": synced}}

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[SyncDomain] Check failed: {e}")
        await execute("UPDATE sync_domain_rule SET status='error', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
        raise HTTPException(status_code=500, detail=str(e))
