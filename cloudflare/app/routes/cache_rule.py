from fastapi import APIRouter, HTTPException, Query
from datetime import datetime
import logging
import httpx

from app.services.db import query_one, query_all, execute
from app.services import cf_client
from app.config import USER_SERVICE_URL

logger = logging.getLogger(__name__)

router = APIRouter()


def _row_to_dict(row: dict) -> dict:
    r = dict(row)
    if r.get("created_at"):
        r["created_at"] = r["created_at"].isoformat()
    if r.get("updated_at"):
        r["updated_at"] = r["updated_at"].isoformat()
    return r


@router.get("")
async def list_cache_rules(projectId: int = Query(...), env: str = Query(None)):
    if env:
        rows = await query_all(
            "SELECT * FROM cf_cache_rule WHERE project_id=%s AND env=%s ORDER BY id DESC",
            (projectId, env),
        )
    else:
        rows = await query_all(
            "SELECT * FROM cf_cache_rule WHERE project_id=%s ORDER BY id DESC",
            (projectId,),
        )
    return {"code": 200, "data": [_row_to_dict(r) for r in rows]}


@router.post("")
async def create_cache_rule(body: dict):
    project_id = body.get("projectId")
    env = (body.get("env") or "").strip()
    name = (body.get("name") or "").strip()
    url = (body.get("url") or "").strip()
    rtype = (body.get("type") or "web").strip()

    if not project_id or not name or not url:
        raise HTTPException(status_code=400, detail="projectId, name, url are required")

    # Check duplicate
    if env:
        existing = await query_one(
            "SELECT id FROM cf_cache_rule WHERE project_id=%s AND name=%s AND env=%s",
            (project_id, name, env),
        )
    else:
        existing = await query_one(
            "SELECT id FROM cf_cache_rule WHERE project_id=%s AND name=%s AND env=''",
            (project_id, name),
        )
    if existing:
        raise HTTPException(status_code=400, detail=f"Rule name already exists: {name}")

    now = datetime.utcnow()
    await execute(
        "INSERT INTO cf_cache_rule (project_id, env, name, url, type, created_at, updated_at) VALUES (%s,%s,%s,%s,%s,%s,%s)",
        (project_id, env, name, url, rtype, now, now),
    )
    row = await query_one(
        "SELECT * FROM cf_cache_rule WHERE project_id=%s AND name=%s AND env=%s ORDER BY id DESC LIMIT 1",
        (project_id, name, env),
    )
    return {"code": 200, "data": _row_to_dict(row), "message": "Rule created"}


@router.put("/{rule_id}")
async def update_cache_rule(rule_id: int, body: dict):
    existing = await query_one("SELECT * FROM cf_cache_rule WHERE id=%s", (rule_id,))
    if not existing:
        raise HTTPException(status_code=404, detail="Rule not found")

    project_id = body.get("projectId", existing["project_id"])

    sets = ["updated_at=%s"]
    args = [datetime.utcnow()]

    if body.get("projectId") is not None:
        sets.append("project_id=%s")
        args.append(body["projectId"])

    for field in ["env", "name", "url", "type"]:
        if body.get(field) is not None:
            sets.append(f"{field}=%s")
            args.append(body[field].strip() if isinstance(body[field], str) else body[field])

    # Check duplicate name
    if body.get("name") is not None:
        new_name = body["name"].strip()
        check_env = body.get("env", existing.get("env", ""))
        dup = await query_one(
            "SELECT id FROM cf_cache_rule WHERE project_id=%s AND name=%s AND env=%s AND id!=%s",
            (project_id, new_name, check_env, rule_id),
        )
        if dup:
            raise HTTPException(status_code=400, detail=f"Rule name already exists: {new_name}")

    args.append(rule_id)
    await execute(f"UPDATE cf_cache_rule SET {', '.join(sets)} WHERE id=%s", tuple(args))
    updated = await query_one("SELECT * FROM cf_cache_rule WHERE id=%s", (rule_id,))
    return {"code": 200, "data": _row_to_dict(updated), "message": "Rule updated"}


@router.delete("/{rule_id}")
async def delete_cache_rule(rule_id: int, projectId: int = Query(...)):
    existing = await query_one(
        "SELECT id FROM cf_cache_rule WHERE id=%s AND project_id=%s",
        (rule_id, projectId),
    )
    if not existing:
        raise HTTPException(status_code=404, detail="Rule not found")

    await execute("DELETE FROM cf_cache_rule WHERE id=%s", (rule_id,))
    return {"code": 200, "data": None, "message": "Rule deleted"}


async def _do_purge(rule: dict, domains: list[str]) -> dict:
    """Common purge logic: resolve domains to zones and purge by prefix."""
    from app.services.mongodb import get_db
    db = await get_db()

    url_path = rule.get("url", "")

    accounts = await query_all("SELECT id FROM account")
    logger.info(f"Purge: found {len(accounts)} accounts in MySQL")

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

    domain_zone_candidates: dict[str, list[dict]] = {d: [] for d in domains}
    for d in domains:
        for z in all_zones:
            zone_name = z["name"]
            if d == zone_name or d.endswith("." + zone_name):
                domain_zone_candidates[d].append(z)

    matched_count = sum(1 for v in domain_zone_candidates.values() if v)
    logger.info(f"Purge: {matched_count}/{len(domains)} domains matched to zones")

    domain_zone_map: dict[str, dict] = {}
    failed: list[dict] = []
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

    zone_info: dict[str, dict] = {}
    for d, info in domain_zone_map.items():
        zid = info["zone_id"]
        if zid not in zone_info:
            zone_info[zid] = {"account_id": info["account_id"], "prefixes": []}
        zone_info[zid]["prefixes"].append(url_path)

    token_cache: dict[str, str] = {}

    async def get_token(account_id: str) -> str:
        if account_id in token_cache:
            return token_cache[account_id]
        row = await query_one("SELECT api_key FROM account WHERE id = %s", (int(account_id),))
        if not row:
            return None
        token_cache[account_id] = row["api_key"]
        return row["api_key"]

    succeeded: list[str] = []
    zone_results = []
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
            logger.info(f"Purge: zone_id={zone_id}, account_id={zinfo['account_id']}, token_prefix={token[:20] if token else 'None'}..., prefixes={zinfo['prefixes']}")
            result = await cf_client.async_purge_by_prefixes(token, zone_id, zinfo["prefixes"])
            logger.info(f"Purge result: {result}")
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


@router.post("/purge")
async def purge_cache_rule(body: dict):
    """Purge cache by prefix for a rule across given domains."""
    rule_id = body.get("ruleId")
    domains = body.get("domains", [])
    if not rule_id or not domains:
        raise HTTPException(status_code=400, detail="ruleId and domains are required")

    rule = await query_one("SELECT * FROM cf_cache_rule WHERE id=%s", (rule_id,))
    if not rule:
        raise HTTPException(status_code=404, detail="Rule not found")

    return await _do_purge(rule, domains)


@router.post("/purgeEverything")
async def purge_everything(body: dict):
    """Purge EVERYTHING for given domains."""
    from app.services.mongodb import get_db
    db = await get_db()

    domains = body.get("domains", [])
    if not domains:
        raise HTTPException(status_code=400, detail="domains is required")

    accounts = await query_all("SELECT id FROM account")
    all_zones: list[dict] = []
    for acc in accounts:
        coll = db[f"account_{acc['id']}_zones"]
        async for zone in coll.find({}):
            all_zones.append({
                "name": zone.get("name", ""),
                "zone_id": zone.get("zone_id", ""),
                "account_id": str(zone.get("account_id", "")),
                "status": zone.get("status", ""),
            })

    domain_zone_candidates: dict[str, list[dict]] = {d: [] for d in domains}
    for d in domains:
        for z in all_zones:
            if d == z["name"] or d.endswith("." + z["name"]):
                domain_zone_candidates[d].append(z)

    domain_zone_map: dict[str, dict] = {}
    failed: list[dict] = []
    for d in domains:
        candidates = domain_zone_candidates[d]
        if not candidates:
            failed.append({"domain": d, "reason": "no zone found"})
            continue
        active = [c for c in candidates if c["status"] == "active"]
        if len(active) > 1:
            failed.append({"domain": d, "reason": f"active in multiple accounts"})
            continue
        domain_zone_map[d] = active[0] if active else candidates[0]

    zone_set: dict[str, dict] = {}
    for d, info in domain_zone_map.items():
        zid = info["zone_id"]
        if zid not in zone_set:
            zone_set[zid] = {"account_id": info["account_id"], "domains": []}
        zone_set[zid]["domains"].append(d)

    token_cache: dict[str, str] = {}
    async def get_token(account_id: str) -> str:
        if account_id in token_cache:
            return token_cache[account_id]
        row = await query_one("SELECT api_key FROM account WHERE id = %s", (int(account_id),))
        if not row:
            return None
        token_cache[account_id] = row["api_key"]
        return row["api_key"]

    succeeded: list[str] = []
    zone_results = []
    for zone_id, zinfo in zone_set.items():
        try:
            token = await get_token(zinfo["account_id"])
            if not token:
                for d in zinfo["domains"]:
                    failed.append({"domain": d, "reason": f"account not found"})
                continue
            await cf_client.async_purge_all(token, zone_id)
            succeeded.extend(zinfo["domains"])
            zone_results.append({"zone_id": zone_id, "account_id": zinfo["account_id"], "domains": zinfo["domains"], "success": True})
        except Exception as e:
            for d in zinfo["domains"]:
                failed.append({"domain": d, "reason": str(e)})
            zone_results.append({"zone_id": zone_id, "account_id": zinfo["account_id"], "domains": zinfo["domains"], "success": False, "error": str(e)})

    return {"code": 200, "data": {"succeeded": succeeded, "failed": failed, "total": len(domains), "zones": zone_results}}


@router.post("/purgeAll")
async def purge_all_cache(body: dict):
    """Purge ALL cached resources for given domains (purge_everything)."""
    from app.services.mongodb import get_db
    db = await get_db()

    domains = body.get("domains", [])
    if not domains:
        raise HTTPException(status_code=400, detail="domains is required")

    accounts = await query_all("SELECT id FROM account")

    all_zones: list[dict] = []
    for acc in accounts:
        coll = db[f"account_{acc['id']}_zones"]
        async for zone in coll.find({}):
            all_zones.append({
                "name": zone.get("name", ""),
                "zone_id": zone.get("zone_id", ""),
                "account_id": str(zone.get("account_id", "")),
                "status": zone.get("status", ""),
            })

    domain_zone_candidates: dict[str, list[dict]] = {d: [] for d in domains}
    for d in domains:
        for z in all_zones:
            zone_name = z["name"]
            if d == zone_name or d.endswith("." + zone_name):
                domain_zone_candidates[d].append(z)

    domain_zone_map: dict[str, dict] = {}
    failed: list[dict] = []
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
        domain_zone_map[d] = chosen

    zone_set: dict[str, dict] = {}
    for d, info in domain_zone_map.items():
        zid = info["zone_id"]
        if zid not in zone_set:
            zone_set[zid] = {"account_id": info["account_id"], "domains": []}
        zone_set[zid]["domains"].append(d)

    token_cache: dict[str, str] = {}

    async def get_token(account_id: str) -> str:
        if account_id in token_cache:
            return token_cache[account_id]
        row = await query_one("SELECT api_key FROM account WHERE id = %s", (int(account_id),))
        if not row:
            return None
        token_cache[account_id] = row["api_key"]
        return row["api_key"]

    succeeded: list[str] = []
    zone_results = []
    for zone_id, zinfo in zone_set.items():
        try:
            token = await get_token(zinfo["account_id"])
            if not token:
                for d in zinfo["domains"]:
                    failed.append({"domain": d, "reason": f"account {zinfo['account_id']} not found"})
                continue
            await cf_client.async_purge_all(token, zone_id)
            succeeded.extend(zinfo["domains"])
            zone_results.append({"zone_id": zone_id, "account_id": zinfo["account_id"], "domains": zinfo["domains"], "success": True})
        except Exception as e:
            logger.error(f"Purge all failed for zone {zone_id}: {e}")
            for d in zinfo["domains"]:
                failed.append({"domain": d, "reason": str(e)})
            zone_results.append({"zone_id": zone_id, "account_id": zinfo["account_id"], "domains": zinfo["domains"], "success": False, "error": str(e)})

    return {"code": 200, "data": {
        "succeeded": succeeded,
        "failed": failed,
        "total": len(domains),
        "zones": zone_results,
    }}


@router.post("/purgeByRule")
async def purge_by_rule(body: dict):
    """Bot 调用：传 ruleId + projectId，CF 自己查域名再清缓存."""
    rule_id = body.get("ruleId")
    project_id = body.get("projectId")
    tg_username = body.get("tgUsername", "bot")
    if not rule_id or not project_id:
        raise HTTPException(status_code=400, detail="ruleId and projectId are required")

    rule = await query_one("SELECT * FROM cf_cache_rule WHERE id=%s", (rule_id,))
    if not rule:
        raise HTTPException(status_code=404, detail="Rule not found")

    # 从 user 服务获取域名
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(f"{USER_SERVICE_URL}/domain/list", params={"projectId": project_id}, headers={"X-Tg-Username": tg_username})
            resp_data = resp.json()
            logger.info(f"PurgeByRule: user service response keys={list(resp_data.keys())}, data type={type(resp_data.get('data')).__name__}")
            domain_list = resp_data.get("data", [])
            if isinstance(domain_list, dict):
                logger.info(f"PurgeByRule: data is dict, keys={list(domain_list.keys())}")
                domain_list = domain_list.get("data", [])
            logger.info(f"PurgeByRule: domain_list len={len(domain_list)}, sample={domain_list[:2] if domain_list else 'empty'}")
            rule_type = rule.get("type", "web")
            rule_env = rule.get("env", "")
            domains = [d["domain"] for d in domain_list if d.get("type") == rule_type and (not rule_env or d.get("env") == rule_env)]
            logger.info(f"PurgeByRule: fetched {len(domain_list)} domains, {len(domains)} {rule_type} type (env={rule_env}): {domains}")
    except Exception as e:
        logger.error(f"Failed to fetch domains from user service: {e}")
        raise HTTPException(status_code=500, detail="Failed to fetch domains")

    if not domains:
        rule_type = rule.get("type", "web")
        logger.warning(f"PurgeByRule: no {rule_type} domains found for projectId={project_id}")
        return {"code": 200, "data": {"succeeded": [], "failed": [], "message": f"No {rule_type} domains found"}}

    result = await _do_purge(rule, domains)
    logger.info(f"PurgeByRule result: {result}")
    return result
