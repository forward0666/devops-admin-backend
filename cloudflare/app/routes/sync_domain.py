import json
import logging
from datetime import datetime
from fastapi import APIRouter, HTTPException

from app.services.db import query_all, query_one, execute
from app.services.mongodb import get_db

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
        from motor.motor_asyncio import AsyncIOMotorClient
        from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_AUTH_DB

        # Step 1: Get groups from MongoDB domain.domain_groups
        logger.info(f"[SyncDomain] Step 1: Querying domain.domain_groups, group_id={group_id}")
        domain_uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/domain?authSource={MONGODB_AUTH_DB}"
        domain_client = AsyncIOMotorClient(domain_uri)
        domain_db = domain_client["domain"]

        from bson import ObjectId
        try:
            group_oid = ObjectId(group_id)
        except Exception:
            group_oid = None
        group = await domain_db["domain_groups"].find_one({"$or": [{"_id": group_oid}, {"_id": group_id}]}) if group_oid else await domain_db["domain_groups"].find_one({"_id": group_id})
        if not group:
            domain_client.close()
            await execute("UPDATE sync_domain_rule SET status='error', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
            raise HTTPException(status_code=404, detail=f"Group '{group_id}' not found in domain_groups")
        logger.info(f"[SyncDomain] Step 1 done: group={group.get('name', group_id)}")

        # Step 2: Get meta from MongoDB domain.domain_meta
        logger.info(f"[SyncDomain] Step 2: Querying domain.domain_meta for groupId={group_id}")
        # Debug: check all unique groupId formats in domain_meta
        sample_meta = await domain_db["domain_meta"].find_one()
        if sample_meta:
            logger.info(f"[SyncDomain] Step 2 debug: sample meta groupId={sample_meta.get('groupId')}, type={type(sample_meta.get('groupId')).__name__}")
        logger.info(f"[SyncDomain] Step 2 debug: rule group_id={group_id}, type={type(group_id).__name__}")
        meta_cursor = domain_db["domain_meta"].find({"groupId": group_id})
        meta_list = await meta_cursor.to_list(length=5000)
        total_meta = await domain_db["domain_meta"].count_documents({})
        logger.info(f"[SyncDomain] Step 2 debug: matched={len(meta_list)}, total_in_collection={total_meta}")
        group_zone_ids = [m.get("zoneId") for m in meta_list if m.get("zoneId")]
        logger.info(f"[SyncDomain] Step 2 done: {len(group_zone_ids)} zone_ids in group")
        domain_client.close()

        if not group_zone_ids:
            await execute("UPDATE sync_domain_rule SET status='ok', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
            return {"code": 200, "data": {"synced": 0, "message": "No zones in group"}}

        # Step 3: Get zone names from MongoDB cloudflare.*_zones, then query DNS records
        logger.info(f"[SyncDomain] Step 3: Querying zone names for {len(group_zone_ids)} zone_ids")
        cf_db = await get_db()
        zone_names = []
        collections = await cf_db.list_collection_names()
        zone_collections = [c for c in collections if c.endswith("_zones")]
        for col_name in zone_collections:
            col = cf_db[col_name]
            for zid in group_zone_ids:
                doc = await col.find_one({"zone_id": zid})
                if doc and doc.get("name"):
                    zone_names.append(doc["name"])
        logger.info(f"[SyncDomain] Step 3 done: {len(zone_names)} zone names: {zone_names}")

        if not zone_names:
            await execute("UPDATE sync_domain_rule SET status='ok', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
            return {"code": 200, "data": {"synced": 0, "message": "No zone names found"}}

        # Step 3b: Get all DNS records (A/CNAME) under these zones
        logger.info(f"[SyncDomain] Step 3b: Querying dns_domains for zone names")
        dns_cursor = cf_db["dns_domains"].find({"zone_name": {"$in": zone_names}, "type": {"$in": ["A", "CNAME"]}})
        dns_records = await dns_cursor.to_list(length=10000)
        domain_names = list({r.get("name", "") for r in dns_records if r.get("name")})
        logger.info(f"[SyncDomain] Step 3b done: {len(dns_records)} DNS records, {len(domain_names)} unique domains")

        # Step 3c: Filter by env keyword (test/uat/dev → filter, prod → keep all)
        if env != "prod":
            before = len(domain_names)
            domain_names = [d for d in domain_names if env.lower() in d.lower()]
            logger.info(f"[SyncDomain] Step 3c: filtered by '{env}', {before} -> {len(domain_names)} domains")

        if not domain_names:
            await execute("UPDATE sync_domain_rule SET status='ok', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
            return {"code": 200, "data": {"synced": 0, "message": "No DNS records found"}}
        zone_names = domain_names  # Use DNS record names instead of zone names

        # Step 4: Write directly to MongoDB project.domains
        logger.info(f"[SyncDomain] Step 4: Writing {len(zone_names)} domains to MongoDB project.domains")
        proj_uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/project?authSource={MONGODB_AUTH_DB}"
        proj_client = AsyncIOMotorClient(proj_uri)
        domains_coll = proj_client["project"]["domains"]

        now = datetime.utcnow()
        synced = 0
        for zn in zone_names:
            if not zn:
                continue
            await domains_coll.update_one(
                {"projectId": int(project_id), "domain": zn},
                {"$set": {"env": env, "updatedAt": now},
                 "$setOnInsert": {"createdAt": now, "projectId": int(project_id), "domain": zn, "type": rtype, "remark": "", "cdn": ""}},
                upsert=True,
            )
            synced += 1

        proj_client.close()
        logger.info(f"[SyncDomain] Step 4 done: {synced} domains upserted")

        await execute("UPDATE sync_domain_rule SET status='ok', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
        return {"code": 200, "data": {"synced": synced}}

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[SyncDomain] Check failed: {e}")
        await execute("UPDATE sync_domain_rule SET status='error', last_check=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))
        raise HTTPException(status_code=500, detail=str(e))
