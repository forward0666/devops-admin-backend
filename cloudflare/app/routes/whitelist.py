from fastapi import APIRouter, HTTPException, Query
from datetime import datetime
from bson import ObjectId
import re
import logging

from app.services.mongodb import get_db
from app.services.db import query_all
from app.services import cf_client

logger = logging.getLogger(__name__)

router = APIRouter()

COLLECTION = "security_rules"
WHITELIST_COLLECTION = "security_whitelist"


def _serialize(doc: dict) -> dict:
    doc["id"] = str(doc.pop("_id"))
    return doc


async def _get_rule_via_rulesets(cf, zone_id, cf_rule_id):
    """Read a single rule via new Rulesets API."""
    resp = cf.rulesets.list(zone_id=zone_id)
    ruleset_id = None
    for r in resp:
        r_dict = r.model_dump()
        if r_dict.get("phase") == "http_request_firewall_custom":
            ruleset_id = r_dict["id"]
            break
    if not ruleset_id:
        raise Exception("No http_request_firewall_custom ruleset found")
    detail = cf.rulesets.get(ruleset_id=ruleset_id, zone_id=zone_id)
    for rule in (detail.rules or []):
        r = rule.model_dump()
        if r.get("id") == cf_rule_id:
            return r
    raise Exception("Rule " + cf_rule_id + " not found in ruleset")


async def _edit_rule_via_rulesets(cf, zone_id, cf_rule_id, expression, action=None, description=None, enabled=None):
    """Edit a rule's expression via new Rulesets API."""
    resp = cf.rulesets.list(zone_id=zone_id)
    ruleset_id = None
    for r in resp:
        r_dict = r.model_dump()
        if r_dict.get("phase") == "http_request_firewall_custom":
            ruleset_id = r_dict["id"]
            break
    if not ruleset_id:
        raise Exception("No http_request_firewall_custom ruleset found")
    kwargs = {
        "rule_id": cf_rule_id,
        "ruleset_id": ruleset_id,
        "zone_id": zone_id,
        "expression": expression,
    }
    if action:
        kwargs["action"] = action
        if action == "skip":
            kwargs["action_parameters"] = {"ruleset": "current"}
    if description is not None:
        kwargs["description"] = description
    if enabled is not None:
        kwargs["enabled"] = enabled
    resp = cf.rulesets.rules.edit(**kwargs)
    return resp


async def _read_and_modify_expression(cf, zone_id, cf_rule_id, modifier_fn):
    """
    Generic helper: read rule via Rulesets API, apply modifier_fn(expr) -> new_expr, write back.
    Returns (old_expr, new_expr, rule_data).
    """
    rule_data = await _get_rule_via_rulesets(cf, zone_id, cf_rule_id)
    expr = rule_data.get("expression", "")
    if not expr:
        raise Exception("expression is empty")
    new_expr = modifier_fn(expr)
    if new_expr is None:
        return (expr, None, rule_data)
    await _edit_rule_via_rulesets(
        cf, zone_id, cf_rule_id, new_expr,
        action=rule_data.get("action", "block"),
        description=rule_data.get("description", ""),
        enabled=not rule_data.get("paused", False),
    )
    return (expr, new_expr, rule_data)


async def _find_token_for_zone(db, accounts, zone_id):
    for acc in accounts:
        coll_name = "account_" + str(acc["id"]) + "_zones"
        zone_doc = await db[coll_name].find_one({"zone_id": zone_id})
        if zone_doc:
            return acc["api_key"]
    return None


@router.get("")
async def list_whitelists(projectId: int = Query(...), env: str = Query(None)):
    db = await get_db()
    query = {"projectId": projectId}
    if env:
        query["env"] = {"$regex": "^" + env + "$", "$options": "i"}
    rows = await db[WHITELIST_COLLECTION].find(query).sort("createdAt", -1).to_list(length=500)
    return {"code": 200, "data": [_serialize(r) for r in rows]}


@router.post("")
async def add_whitelist_ip(body: dict):
    project_id = body.get("projectId")
    rule_id = (body.get("ruleId") or "").strip()
    ip = (body.get("ip") or "").strip()
    logger.info(f"========== Add Whitelist ========== projectId={project_id}, ruleId={rule_id}, ip={ip}")

    if not project_id or not rule_id or not ip:
        raise HTTPException(status_code=400, detail="projectId, ruleId and ip are required")

    db = await get_db()

    try:
        oid = ObjectId(rule_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid ruleId")

    rule = await db[COLLECTION].find_one({"_id": oid, "projectId": project_id})
    if not rule:
        raise HTTPException(status_code=404, detail="Security rule not found")

    entries = rule.get("entries", [])
    logger.info(f"[1] Rule: {rule.get('name')}, entries: {entries}")
    if not entries:
        raise HTTPException(status_code=400, detail="Rule has no entries")

    accounts = await query_all("SELECT id, api_key FROM account")
    logger.info(f"[2] Accounts: {[a['id'] for a in accounts]}")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts configured")

    results = []
    errors = []

    for entry in entries:
        zone_id = entry.get("zoneId", "")
        cf_rule_id = entry.get("ruleId", "")
        logger.info(f"[3] Entry: zoneId={zone_id}, cfRuleId={cf_rule_id}")

        if not zone_id or not cf_rule_id:
            errors.append({"entry": entry, "reason": "missing zoneId or ruleId"})
            continue

        token = await _find_token_for_zone(db, accounts, zone_id)
        if not token:
            logger.error(f"[4] Account not found for zone {zone_id}")
            errors.append({"zoneId": zone_id, "reason": "account not found for zone"})
            continue
        logger.info(f"[4] Found token for zone")

        def add_ip_modifier(expr):
            for pat in [r'\(ip\.src in \{([^}]*)\}\)', r'ip\.src in \{([^}]*)\}']:
                match = re.search(pat, expr)
                if match:
                    existing_ips = [x.strip() for x in match.group(1).replace(',', ' ').split() if x.strip()]
                    logger.info(f"[6] Current IPs: {existing_ips}, adding: {ip}")
                    if ip in existing_ips:
                        logger.info(f"[6] IP already exists, skipping CF update")
                        results.append({"zoneId": zone_id, "ruleId": cf_rule_id, "expression": expr, "skipped": True})
                        return None  # signal: skip
                    existing_ips.append(ip)
                    new_expr = re.sub(pat, "(ip.src in {" + " ".join(existing_ips) + "})", expr)
                    logger.info(f"[6] New expression: '{new_expr}'")
                    return new_expr
            return None

        try:
            cf = await cf_client.async_get_client(token)
            old_expr, new_expr, rule_data = await _read_and_modify_expression(cf, zone_id, cf_rule_id, add_ip_modifier)
            if new_expr:
                logger.info(f"[7] SUCCESS")
                results.append({"zoneId": zone_id, "ruleId": cf_rule_id, "expression": new_expr})
            # if new_expr is None, modifier already appended result (skipped) or no pattern matched
        except Exception as e:
            logger.error(f"[5/7] CF API error: {type(e).__name__}: {e}")
            errors.append({"zoneId": zone_id, "ruleId": cf_rule_id, "reason": f"CF API error: {e}"})

    doc = {
        "projectId": project_id,
        "env": (body.get("env") or "").strip(),
        "ruleName": rule.get("name", ""),
        "ruleId": rule_id,
        "username": (body.get("username") or "").strip(),
        "ip": ip,
        "operator": (body.get("operator") or "").strip(),
        "results": results,
        "errors": errors,
        "createdAt": datetime.utcnow(),
    }
    await db[WHITELIST_COLLECTION].insert_one(doc)
    logger.info(f"[8] Done. updated={len(results)}, errors={len(errors)}")

    if errors and not results:
        raise HTTPException(status_code=400, detail={"message": "All entries failed", "errors": errors})

    return {"code": 200, "data": {"updated": len(results), "errors": errors}, "message": "已添加至规则 [" + rule.get("name", "") + "]"}


@router.put("")
async def update_whitelist_ip(body: dict):
    """Update IP: check shared users before removing old IP"""
    project_id = body.get("projectId")
    rule_id = (body.get("ruleId") or "").strip()
    old_ip = (body.get("oldIp") or "").strip()
    new_ip = (body.get("newIp") or "").strip()

    logger.info(f"========== Update IP ========== projectId={project_id}, ruleId={rule_id}, oldIp={old_ip}, newIp={new_ip}")

    if not project_id or not old_ip or not new_ip:
        raise HTTPException(status_code=400, detail="projectId, oldIp and newIp are required")

    if old_ip == new_ip:
        raise HTTPException(status_code=400, detail="oldIp and newIp are the same")

    db = await get_db()

    shared_query = {"ip": old_ip, "projectId": project_id}
    if rule_id:
        shared_query["ruleId"] = rule_id
    shared_count = await db[WHITELIST_COLLECTION].count_documents(shared_query)
    logger.info(f"[1] Records sharing IP {old_ip}: {shared_count}")

    if shared_count <= 1:
        logger.info(f"[2] Only one user, removing old IP from CF")
        if rule_id:
            await _remove_ip_from_cf(db, project_id, rule_id, old_ip)
    else:
        logger.info(f"[2] {shared_count} users share this IP, skipping CF remove")

    if rule_id:
        logger.info(f"[3] Adding new IP to CF")
        await _add_ip_to_cf(db, project_id, rule_id, new_ip)

    update_query = {"ip": old_ip, "projectId": project_id}
    if rule_id:
        update_query["ruleId"] = rule_id
    record_id = body.get("id")
    if record_id:
        try:
            await db[WHITELIST_COLLECTION].update_one(
                {"_id": ObjectId(record_id), "projectId": project_id},
                {"$set": {"ip": new_ip, "updatedAt": datetime.utcnow()}}
            )
        except Exception:
            await db[WHITELIST_COLLECTION].update_one(update_query, {"$set": {"ip": new_ip, "updatedAt": datetime.utcnow()}})
    else:
        await db[WHITELIST_COLLECTION].update_one(update_query, {"$set": {"ip": new_ip, "updatedAt": datetime.utcnow()}})

    logger.info(f"[4] Done")
    return {"code": 200, "message": "IP updated from " + old_ip + " to " + new_ip}


async def _remove_ip_from_cf(db, project_id, rule_id, ip):
    try:
        oid = ObjectId(rule_id)
    except Exception:
        return

    rule = await db[COLLECTION].find_one({"_id": oid, "projectId": project_id})
    if not rule:
        return

    entries = rule.get("entries", [])
    accounts = await query_all("SELECT id, api_key FROM account")

    for entry in entries:
        zone_id = entry.get("zoneId", "")
        cf_rule_id = entry.get("ruleId", "")
        if not zone_id or not cf_rule_id:
            continue

        token = await _find_token_for_zone(db, accounts, zone_id)
        if not token:
            continue

        def remove_ip_modifier(expr):
            for pat in [r'\(ip\.src in \{([^}]*)\}\)', r'ip\.src in \{([^}]*)\}']:
                match = re.search(pat, expr)
                if match:
                    ips = [x.strip() for x in match.group(1).replace(',', ' ').split() if x.strip()]
                    if ip in ips:
                        ips.remove(ip)
                    new_expr = re.sub(pat, "(ip.src in {" + " ".join(ips) + "})", expr) if ips else re.sub(pat, "", expr).strip()
                    return new_expr if new_expr else None
            return None

        try:
            cf = await cf_client.async_get_client(token)
            await _read_and_modify_expression(cf, zone_id, cf_rule_id, remove_ip_modifier)
        except Exception as e:
            logger.error(f"_remove_ip_from_cf error: {e}")


async def _add_ip_to_cf(db, project_id, rule_id, ip):
    try:
        oid = ObjectId(rule_id)
    except Exception:
        return

    rule = await db[COLLECTION].find_one({"_id": oid, "projectId": project_id})
    if not rule:
        return

    entries = rule.get("entries", [])
    accounts = await query_all("SELECT id, api_key FROM account")

    for entry in entries:
        zone_id = entry.get("zoneId", "")
        cf_rule_id = entry.get("ruleId", "")
        if not zone_id or not cf_rule_id:
            continue

        token = await _find_token_for_zone(db, accounts, zone_id)
        if not token:
            continue

        def add_ip_modifier(expr):
            for pat in [r'\(ip\.src in \{([^}]*)\}\)', r'ip\.src in \{([^}]*)\}']:
                match = re.search(pat, expr)
                if match:
                    ips = [x.strip() for x in match.group(1).replace(',', ' ').split() if x.strip()]
                    if ip not in ips:
                        ips.append(ip)
                    new_expr = re.sub(pat, "(ip.src in {" + " ".join(ips) + "})", expr)
                    return new_expr
            return None

        try:
            cf = await cf_client.async_get_client(token)
            await _read_and_modify_expression(cf, zone_id, cf_rule_id, add_ip_modifier)
        except Exception as e:
            logger.error(f"_add_ip_to_cf error: {e}")


@router.delete("/remove")
async def remove_whitelist_ip(projectId: int = Query(...), ruleId: str = Query(None), ip: str = Query(...), username: str = Query(None)):
    logger.info(f"========== Remove IP ========== projectId={projectId}, ruleId={ruleId}, ip={ip}")
    db = await get_db()

    results = []
    errors = []

    if ruleId:
        try:
            oid = ObjectId(ruleId)
        except Exception:
            raise HTTPException(status_code=400, detail="Invalid ruleId")

        rule = await db[COLLECTION].find_one({"_id": oid, "projectId": projectId})
        if not rule:
            raise HTTPException(status_code=404, detail="Security rule not found")

        entries = rule.get("entries", [])
        logger.info(f"[1] Rule: {rule.get('name')}, entries: {entries}")
        if not entries:
            raise HTTPException(status_code=400, detail="Rule has no entries")

        accounts = await query_all("SELECT id, api_key FROM account")
        logger.info(f"[2] Accounts: {[a['id'] for a in accounts]}")
        if not accounts:
            raise HTTPException(status_code=400, detail="No CF accounts configured")

        for entry in entries:
            zone_id = entry.get("zoneId", "")
            cf_rule_id = entry.get("ruleId", "")
            logger.info(f"[3] Entry: zoneId={zone_id}, cfRuleId={cf_rule_id}")
            if not zone_id or not cf_rule_id:
                errors.append({"entry": entry, "reason": "missing zoneId or ruleId"})
                continue

            token = await _find_token_for_zone(db, accounts, zone_id)
            if not token:
                logger.error(f"[4] Account not found for zone {zone_id}")
                errors.append({"zoneId": zone_id, "reason": "account not found"})
                continue
            logger.info(f"[4] Found token for zone")

            ip_found = [False]
            original_expr = [None]

            def remove_ip_modifier(expr):
                original_expr[0] = expr
                for pat in [r'\(ip\.src in \{([^}]*)\}\)', r'ip\.src in \{([^}]*)\}']:
                    match = re.search(pat, expr)
                    if match:
                        ips = [x.strip() for x in match.group(1).replace(',', ' ').split() if x.strip()]
                        logger.info(f"[6] Current IPs: {ips}, removing: {ip}")
                        if ip in ips:
                            ips.remove(ip)
                            ip_found[0] = True
                        else:
                            logger.warning(f"[6] IP {ip} not found in expression, skipping CF update")
                        logger.info(f"[6] Remaining IPs: {ips}")
                        if ips:
                            return re.sub(pat, "(ip.src in {" + " ".join(ips) + "})", expr)
                        else:
                            return re.sub(pat, "", expr).strip() or None
                return None

            try:
                cf = await cf_client.async_get_client(token)
                old_expr, new_expr, rule_data = await _read_and_modify_expression(cf, zone_id, cf_rule_id, remove_ip_modifier)
                logger.info(f"[5] Expression: '{old_expr}'")
                if not ip_found[0]:
                    results.append({"zoneId": zone_id, "ruleId": cf_rule_id, "expression": old_expr, "ipNotFound": True})
                elif new_expr:
                    logger.info(f"[7] SUCCESS")
                    results.append({"zoneId": zone_id, "ruleId": cf_rule_id, "expression": new_expr})
                elif new_expr is None:
                    # Expression became empty after removal — rule still exists but with empty expr
                    logger.warning(f"[7] Expression became empty after removing IP")
                    results.append({"zoneId": zone_id, "ruleId": cf_rule_id, "expression": "", "empty": True})
            except Exception as e:
                logger.error(f"[5/7] CF API error: {type(e).__name__}: {e}")
                errors.append({"zoneId": zone_id, "ruleId": cf_rule_id, "reason": f"CF API error: {e}"})
    else:
        logger.info("[1] No ruleId provided, skipping CF update")

    delete_query = {"ip": ip, "projectId": projectId}
    if ruleId:
        delete_query["ruleId"] = ruleId
    if username:
        delete_query["username"] = username
    delete_result = await db[WHITELIST_COLLECTION].delete_many(delete_query)
    logger.info(f"[8] Deleted {delete_result.deleted_count} whitelist records")

    if errors and not results:
        raise HTTPException(status_code=400, detail={"message": "All entries failed", "errors": errors})

    logger.info(f"========== Remove IP Done ==========")
    return {"code": 200, "data": {"removed": len(results), "deleted": delete_result.deleted_count, "errors": errors}, "message": "IP removed, " + str(delete_result.deleted_count) + " record(s) deleted"}


@router.delete("/{wl_id}")
async def delete_whitelist(wl_id: str, projectId: int = Query(...)):
    db = await get_db()
    try:
        oid = ObjectId(wl_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid id")

    existing = await db[WHITELIST_COLLECTION].find_one({"_id": oid, "projectId": projectId})
    if not existing:
        raise HTTPException(status_code=404, detail="Whitelist not found")

    await db[WHITELIST_COLLECTION].delete_one({"_id": oid})
    return {"code": 200, "data": None, "message": "Whitelist deleted"}
