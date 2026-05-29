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


@router.get("")
async def list_whitelists(projectId: int = Query(...), env: str = Query(None)):
    db = await get_db()
    query = {"projectId": projectId}
    if env:
        query["env"] = {"$regex": f"^{env}$", "$options": "i"}
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

        token = None
        for acc in accounts:
            coll_name = f"account_{acc['id']}_zones"
            zone_doc = await db[coll_name].find_one({"zone_id": zone_id})
            if zone_doc:
                token = acc["api_key"]
                break

        if not token:
            logger.error(f"[4] Account not found for zone {zone_id}")
            errors.append({"zoneId": zone_id, "reason": "account not found for zone"})
            continue
        logger.info(f"[4] Found token for zone")

        try:
            cf = cf_client.get_client(token)
            resp = cf.firewall.rules.get(rule_id=cf_rule_id, zone_id=zone_id)
            raw = resp.model_dump()
            filter_data = raw.get("filter") or {}
            expr = (filter_data.get("expression", "") if isinstance(filter_data, dict) else "") or raw.get("expression", "") or ""
            filter_id = (filter_data.get("id", "") if isinstance(filter_data, dict) else "") or ""
            logger.info(f"[5] Expression: '{expr}', filter_id={filter_id}")
        except Exception as e:
            logger.error(f"[5] CF API error: {type(e).__name__}: {e}")
            errors.append({"zoneId": zone_id, "ruleId": cf_rule_id, "reason": f"CF API error: {e}"})
            continue

        if not expr:
            logger.error(f"[5] Expression is empty")
            errors.append({"zoneId": zone_id, "ruleId": cf_rule_id, "reason": "expression is empty"})
            continue

        new_expr = None
        for pat in [r'\(ip\.src in \{([^}]*)\}\)', r'ip\.src in \{([^}]*)\}']:
            match = re.search(pat, expr)
            if match:
                existing_ips = [x.strip() for x in match.group(1).replace(',', ' ').split() if x.strip()]
                logger.info(f"[6] Current IPs: {existing_ips}, adding: {ip}")
                if ip in existing_ips:
                    logger.info(f"[6] IP already exists, skipping CF update")
                    results.append({"zoneId": zone_id, "ruleId": cf_rule_id, "expression": expr, "skipped": True})
                    break
                existing_ips.append(ip)
                new_expr = re.sub(pat, f"(ip.src in {{{' '.join(existing_ips)}}})", expr)
                logger.info(f"[6] New expression: '{new_expr}'")
                break

        if not new_expr:
            logger.error(f"[6] No pattern matched for: '{expr}'")
            errors.append({"zoneId": zone_id, "ruleId": cf_rule_id, "reason": f"expression has no ip.src in pattern: {expr}"})
            continue

        try:
            if filter_id:
                logger.info(f"[7] Updating filter {filter_id}")
                cf.filters.update(filter_id=filter_id, zone_id=zone_id, expression=new_expr)
            else:
                logger.info(f"[7] Creating new filter")
                cf.filters.create(zone_id=zone_id, expression=new_expr)
            logger.info(f"[7] SUCCESS")
            results.append({"zoneId": zone_id, "ruleId": cf_rule_id, "expression": new_expr})
        except Exception as e:
            logger.error(f"[7] CF update error: {type(e).__name__}: {e}")
            errors.append({"zoneId": zone_id, "ruleId": cf_rule_id, "reason": f"CF update error: {e}"})

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

    return {"code": 200, "data": {"updated": len(results), "errors": errors}, "message": f"已添加至规则 [{rule.get('name', '')}]"}


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

    # Check how many records share the same old IP
    shared_query: dict = {"ip": old_ip, "projectId": project_id}
    if rule_id:
        shared_query["ruleId"] = rule_id
    shared_count = await db[WHITELIST_COLLECTION].count_documents(shared_query)
    logger.info(f"[1] Records sharing IP {old_ip}: {shared_count}")

    # Step 1: Remove old IP from CF only if no other users share it
    if shared_count <= 1:
        logger.info(f"[2] Only one user, removing old IP from CF")
        if rule_id:
            await _remove_ip_from_cf(db, project_id, rule_id, old_ip)
    else:
        logger.info(f"[2] {shared_count} users share this IP, skipping CF remove")

    # Step 2: Add new IP to CF
    if rule_id:
        logger.info(f"[3] Adding new IP to CF")
        await _add_ip_to_cf(db, project_id, rule_id, new_ip)

    # Step 3: Update only THIS user's whitelist record (not all records with old IP)
    update_query: dict = {"ip": old_ip, "projectId": project_id}
    if rule_id:
        update_query["ruleId"] = rule_id
    # Only update one record (the one being edited)
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
    return {"code": 200, "message": f"IP updated from {old_ip} to {new_ip}"}


async def _remove_ip_from_cf(db, project_id: int, rule_id: str, ip: str):
    """Helper: remove IP from CF filter expression"""
    try:
        oid = ObjectId(rule_id)
    except Exception:
        return None

    rule = await db[COLLECTION].find_one({"_id": oid, "projectId": project_id})
    if not rule:
        return None

    entries = rule.get("entries", [])
    accounts = await query_all("SELECT id, api_key FROM account")

    for entry in entries:
        zone_id = entry.get("zoneId", "")
        cf_rule_id = entry.get("ruleId", "")
        if not zone_id or not cf_rule_id:
            continue

        token = None
        for acc in accounts:
            if await db[f"account_{acc['id']}_zones"].find_one({"zone_id": zone_id}):
                token = acc["api_key"]
                break
        if not token:
            continue

        try:
            cf = cf_client.get_client(token)
            resp = cf.firewall.rules.get(rule_id=cf_rule_id, zone_id=zone_id)
            raw = resp.model_dump()
            filter_data = raw.get("filter") or {}
            expr = (filter_data.get("expression", "") if isinstance(filter_data, dict) else "") or ""
            filter_id = (filter_data.get("id", "") if isinstance(filter_data, dict) else "") or ""

            for pat in [r'\(ip\.src in \{([^}]*)\}\)', r'ip\.src in \{([^}]*)\}']:
                match = re.search(pat, expr)
                if match:
                    ips = [x.strip() for x in match.group(1).replace(',', ' ').split() if x.strip()]
                    if ip in ips:
                        ips.remove(ip)
                    new_expr = re.sub(pat, f"(ip.src in {{{' '.join(ips)}}})", expr) if ips else re.sub(pat, "", expr).strip()
                    if filter_id and new_expr:
                        cf.filters.update(filter_id=filter_id, zone_id=zone_id, expression=new_expr)
                    break
        except Exception as e:
            logger.error(f"_remove_ip_from_cf error: {e}")


async def _add_ip_to_cf(db, project_id: int, rule_id: str, ip: str):
    """Helper: add IP to CF filter expression"""
    try:
        oid = ObjectId(rule_id)
    except Exception:
        return None

    rule = await db[COLLECTION].find_one({"_id": oid, "projectId": project_id})
    if not rule:
        return None

    entries = rule.get("entries", [])
    accounts = await query_all("SELECT id, api_key FROM account")

    for entry in entries:
        zone_id = entry.get("zoneId", "")
        cf_rule_id = entry.get("ruleId", "")
        if not zone_id or not cf_rule_id:
            continue

        token = None
        for acc in accounts:
            if await db[f"account_{acc['id']}_zones"].find_one({"zone_id": zone_id}):
                token = acc["api_key"]
                break
        if not token:
            continue

        try:
            cf = cf_client.get_client(token)
            resp = cf.firewall.rules.get(rule_id=cf_rule_id, zone_id=zone_id)
            raw = resp.model_dump()
            filter_data = raw.get("filter") or {}
            expr = (filter_data.get("expression", "") if isinstance(filter_data, dict) else "") or ""
            filter_id = (filter_data.get("id", "") if isinstance(filter_data, dict) else "") or ""

            for pat in [r'\(ip\.src in \{([^}]*)\}\)', r'ip\.src in \{([^}]*)\}']:
                match = re.search(pat, expr)
                if match:
                    ips = [x.strip() for x in match.group(1).replace(',', ' ').split() if x.strip()]
                    if ip not in ips:
                        ips.append(ip)
                    new_expr = re.sub(pat, f"(ip.src in {{{' '.join(ips)}}})", expr)
                    if filter_id:
                        cf.filters.update(filter_id=filter_id, zone_id=zone_id, expression=new_expr)
                    break
        except Exception as e:
            logger.error(f"_add_ip_to_cf error: {e}")


@router.delete("/remove")
async def remove_whitelist_ip(projectId: int = Query(...), ruleId: str = Query(None), ip: str = Query(...), username: str = Query(None)):
    """Remove IP from CF filter expression"""
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

            token = None
            for acc in accounts:
                coll_name = f"account_{acc['id']}_zones"
                if await db[coll_name].find_one({"zone_id": zone_id}):
                    token = acc["api_key"]
                    break
            if not token:
                logger.error(f"[4] Account not found for zone {zone_id}")
                errors.append({"zoneId": zone_id, "reason": "account not found"})
                continue
            logger.info(f"[4] Found token for zone")

            try:
                cf = cf_client.get_client(token)
                resp = cf.firewall.rules.get(rule_id=cf_rule_id, zone_id=zone_id)
                raw = resp.model_dump()
                filter_data = raw.get("filter") or {}
                expr = (filter_data.get("expression", "") if isinstance(filter_data, dict) else "") or ""
                filter_id = (filter_data.get("id", "") if isinstance(filter_data, dict) else "") or ""
                logger.info(f"[5] Expression: '{expr}', filter_id={filter_id}")
            except Exception as e:
                logger.error(f"[5] CF API error: {type(e).__name__}: {e}")
                errors.append({"zoneId": zone_id, "reason": f"CF API error: {e}"})
                continue

            if not expr:
                logger.error(f"[5] Expression is empty")
                errors.append({"zoneId": zone_id, "reason": "expression is empty"})
                continue

            new_expr = None
            ip_found = False
            for pat in [r'\(ip\.src in \{([^}]*)\}\)', r'ip\.src in \{([^}]*)\}']:
                match = re.search(pat, expr)
                if match:
                    ips = [x.strip() for x in match.group(1).replace(',', ' ').split() if x.strip()]
                    logger.info(f"[6] Current IPs: {ips}, removing: {ip}")
                    if ip in ips:
                        ips.remove(ip)
                        ip_found = True
                    else:
                        logger.warning(f"[6] IP {ip} not found in expression, skipping CF update")
                    logger.info(f"[6] Remaining IPs: {ips}")
                    if ips:
                        new_expr = re.sub(pat, f"(ip.src in {{{' '.join(ips)}}})", expr)
                    else:
                        new_expr = re.sub(pat, "", expr).strip()
                    logger.info(f"[6] New expression: '{new_expr}'")
                    break

            if not ip_found:
                results.append({"zoneId": zone_id, "ruleId": cf_rule_id, "expression": expr, "ipNotFound": True})
                continue

            if new_expr is None:
                logger.error(f"[6] No pattern matched for: '{expr}'")
                errors.append({"zoneId": zone_id, "reason": f"expression has no ip.src pattern: {expr}"})
                continue

            try:
                if filter_id:
                    if new_expr:
                        logger.info(f"[7] Updating filter {filter_id}")
                        cf.filters.update(filter_id=filter_id, zone_id=zone_id, expression=new_expr)
                    else:
                        logger.info(f"[7] Deleting filter {filter_id} (no IPs left)")
                        cf.filters.delete(filter_id=filter_id, zone_id=zone_id)
                    logger.info(f"[7] SUCCESS")
                else:
                    logger.warning(f"[7] No filter_id, skipping update")
                results.append({"zoneId": zone_id, "ruleId": cf_rule_id, "expression": new_expr})
            except Exception as e:
                logger.error(f"[7] CF update error: {type(e).__name__}: {e}")
                errors.append({"zoneId": zone_id, "reason": f"CF update error: {e}"})
    else:
        logger.info("[1] No ruleId provided, skipping CF update")

    delete_query: dict = {"ip": ip, "projectId": projectId}
    if ruleId:
        delete_query["ruleId"] = ruleId
    if username:
        delete_query["username"] = username
    delete_result = await db[WHITELIST_COLLECTION].delete_many(delete_query)
    logger.info(f"[8] Deleted {delete_result.deleted_count} whitelist records")

    if errors and not results:
        raise HTTPException(status_code=400, detail={"message": "All entries failed", "errors": errors})

    logger.info(f"========== Remove IP Done ==========")
    return {"code": 200, "data": {"removed": len(results), "deleted": delete_result.deleted_count, "errors": errors}, "message": f"IP removed, {delete_result.deleted_count} record(s) deleted"}


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
