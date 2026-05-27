from fastapi import APIRouter, HTTPException, Query
from datetime import datetime
from bson import ObjectId
import logging

from app.services.mongodb import get_db
from app.services.db import query_one, query_all
from app.services import cf_client

logger = logging.getLogger(__name__)

router = APIRouter()

COLLECTION = "cache_rules"


def _serialize(doc: dict) -> dict:
    doc["id"] = str(doc.pop("_id"))
    return doc


@router.get("")
async def list_cache_rules(projectId: int = Query(...), env: str = Query(None)):
    db = await get_db()
    query = {"projectId": projectId}
    if env:
        query["env"] = env
    rows = await db[COLLECTION].find(query).sort("createdAt", -1).to_list(length=500)
    return {"code": 200, "data": [_serialize(r) for r in rows]}


@router.post("")
async def create_cache_rule(body: dict):
    project_id = body.get("projectId")
    env = (body.get("env") or "").strip()
    name = (body.get("name") or "").strip()
    url = (body.get("url") or "").strip()
    callback_data = body.get("callbackData", "")

    if not project_id or not name or not url:
        raise HTTPException(status_code=400, detail="projectId, name, url are required")

    db = await get_db()
    dup_query = {"projectId": project_id, "name": name}
    if env:
        dup_query["env"] = env
    existing = await db[COLLECTION].find_one(dup_query)
    if existing:
        raise HTTPException(status_code=400, detail=f"Rule name already exists: {name}")

    doc = {
        "projectId": project_id,
        "env": env,
        "name": name,
        "url": url,
        "callbackData": callback_data,
        "createdAt": datetime.utcnow(),
        "updatedAt": datetime.utcnow(),
    }
    result = await db[COLLECTION].insert_one(doc)
    doc["_id"] = result.inserted_id
    return {"code": 200, "data": _serialize(doc), "message": "Rule created"}


@router.put("/{rule_id}")
async def update_cache_rule(rule_id: str, body: dict):
    project_id = body.get("projectId")
    env = body.get("env")
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
    if env is not None:
        update_fields["env"] = env.strip()
    if body.get("name") is not None:
        new_name = body["name"].strip()
        dup_query = {"projectId": project_id, "name": new_name, "_id": {"$ne": oid}}
        cur_env = update_fields.get("env", existing.get("env", ""))
        if cur_env:
            dup_query["env"] = cur_env
        dup = await db[COLLECTION].find_one(dup_query)
        if dup:
            raise HTTPException(status_code=400, detail=f"Rule name already exists: {new_name}")
        update_fields["name"] = new_name
    if body.get("url") is not None:
        update_fields["url"] = body["url"].strip()
    if body.get("callbackData") is not None:
        update_fields["callbackData"] = body["callbackData"]

    await db[COLLECTION].update_one({"_id": oid}, {"$set": update_fields})
    updated = await db[COLLECTION].find_one({"_id": oid})
    return {"code": 200, "data": _serialize(updated), "message": "Rule updated"}


@router.delete("/{rule_id}")
async def delete_cache_rule(rule_id: str, projectId: int = Query(...)):
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


@router.post("/purge")
async def purge_cache_rule(body: dict):
    """Purge cache by prefix for a rule across given domains."""
    rule_id = body.get("ruleId")
    domains = body.get("domains", [])

    if not rule_id or not domains:
        raise HTTPException(status_code=400, detail="ruleId and domains are required")

    db = await get_db()
    try:
        oid = ObjectId(rule_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid rule id")

    rule = await db[COLLECTION].find_one({"_id": oid})
    if not rule:
        raise HTTPException(status_code=404, detail="Rule not found")

    url_path = rule.get("url", "")

    # Get all accounts from MySQL, then query each account's zones collection directly
    accounts = await query_all("SELECT id FROM account")
    logger.info(f"Purge: found {len(accounts)} accounts in MySQL")

    # Collect all zones from all accounts
    all_zones: list[dict] = []
    for acc in accounts:
        acc_id = acc["id"]
        coll_name = f"account_{acc_id}_zones"
        coll = db[coll_name]
        async for zone in coll.find({}):
            all_zones.append({
                "name": zone.get("name", ""),
                "zone_id": zone.get("zone_id", ""),
                "account_id": str(zone.get("account_id", "")),
                "status": zone.get("status", ""),
            })
    logger.info(f"Purge: loaded {len(all_zones)} zones from {len(accounts)} accounts")

    if not all_zones:
        raise HTTPException(status_code=400, detail="No zones synced. Please sync zones first.")

    # For each domain, find zones whose name is a suffix (e.g. jhdevops.com matches test.api.jhdevops.com)
    domain_zone_candidates: dict[str, list[dict]] = {d: [] for d in domains}
    for d in domains:
        for z in all_zones:
            zone_name = z["name"]
            if d == zone_name or d.endswith("." + zone_name):
                domain_zone_candidates[d].append(z)

    matched_count = sum(1 for v in domain_zone_candidates.values() if v)
    logger.info(f"Purge: {matched_count}/{len(domains)} domains matched to zones")

    # Resolve each domain: prefer active zone, report conflicts
    domain_zone_map: dict[str, dict] = {}  # domain -> {zone_id, account_id}
    failed: list[dict] = []  # [{domain, reason}]
    for d in domains:
        candidates = domain_zone_candidates[d]
        if not candidates:
            failed.append({"domain": d, "reason": "no zone found"})
            continue

        active = [c for c in candidates if c["status"] == "active"]
        if len(active) > 1:
            accounts_str = ", ".join(c["account_id"] for c in active)
            failed.append({"domain": d, "reason": f"active in multiple accounts ({accounts_str})"})
            continue

        chosen = active[0] if active else candidates[0]
        if not active:
            logger.warning(f"Domain {d} has no active zone, using zone {chosen['zone_id']} (status={chosen['status']})")

        domain_zone_map[d] = chosen

    # Group prefixes by (zone_id, account_id)
    zone_info: dict[str, dict] = {}  # zone_id -> {account_id, prefixes}
    for d, info in domain_zone_map.items():
        zid = info["zone_id"]
        if zid not in zone_info:
            zone_info[zid] = {"account_id": info["account_id"], "prefixes": []}
        zone_info[zid]["prefixes"].append(f"https://{d}{url_path}")

    # Cache tokens by account_id
    token_cache: dict[str, str] = {}

    async def get_token(account_id: str) -> str:
        if account_id in token_cache:
            return token_cache[account_id]
        row = await query_one("SELECT api_key FROM account WHERE id = %s", (int(account_id),))
        if not row:
            return None
        token_cache[account_id] = row["api_key"]
        return row["api_key"]

    # Purge each zone, track per-domain result
    succeeded: list[str] = []
    zone_results = []
    # Build reverse map: zone_id -> list of domains
    zone_domains: dict[str, list[str]] = {}
    for d, info in domain_zone_map.items():
        zid = info["zone_id"]
        if zid not in zone_domains:
            zone_domains[zid] = []
        zone_domains[zid].append(d)

    for zone_id, zinfo in zone_info.items():
        try:
            token = await get_token(zinfo["account_id"])
            if not token:
                for d in zone_domains.get(zone_id, []):
                    failed.append({"domain": d, "reason": f"account {zinfo['account_id']} not found"})
                continue
            cf_client.purge_by_prefixes(token, zone_id, zinfo["prefixes"])
            succeeded.extend(zone_domains.get(zone_id, []))
            zone_results.append({"zone_id": zone_id, "account_id": zinfo["account_id"], "domains": zone_domains.get(zone_id, []), "success": True})
        except Exception as e:
            logger.error(f"Purge failed for zone {zone_id}: {e}")
            for d in zone_domains.get(zone_id, []):
                failed.append({"domain": d, "reason": str(e)})
            zone_results.append({"zone_id": zone_id, "account_id": zinfo["account_id"], "domains": zone_domains.get(zone_id, []), "success": False, "error": str(e)})

    return {"code": 200, "data": {
        "succeeded": succeeded,
        "failed": failed,
        "total": len(domains),
        "zones": zone_results,
    }}
