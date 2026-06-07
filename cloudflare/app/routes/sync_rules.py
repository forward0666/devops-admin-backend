from fastapi import APIRouter, HTTPException
from datetime import datetime
import logging

from app.services.db import execute, query_one, query_all

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE = "cf_sync_rule"


# ── Routes ──

@router.get("")
async def list_sync_rules(account_id: int = -1):
    """List all sync rules for an account (account_id=-1 for all)"""
    if account_id == -1:
        rows = await query_all(f"SELECT * FROM `{TABLE}` ORDER BY created_at DESC")
    else:
        rows = await query_all(
            f"SELECT * FROM `{TABLE}` WHERE account_id = %s ORDER BY created_at DESC",
            (account_id,)
        )
    return {"code": 200, "data": rows}


@router.post("")
async def create_sync_rule(body: dict):
    """Create a new sync rule"""
    account_id = body.get("account_id")
    name = (body.get("name") or "").strip()
    description = (body.get("description") or "").strip()
    source_zone_id = body.get("sourceZoneId")
    target_zone_ids = body.get("targetZoneIds") or []
    rule_types = body.get("ruleTypes") or []

    if not name:
        raise HTTPException(status_code=400, detail="Name is required")
    if not source_zone_id:
        raise HTTPException(status_code=400, detail="Source domain is required")
    if not target_zone_ids:
        raise HTTPException(status_code=400, detail="At least one target domain is required")
    if not rule_types:
        raise HTTPException(status_code=400, detail="At least one rule type is required")

    import json
    await execute(
        f"INSERT INTO `{TABLE}` (account_id, name, description, source_zone_id, target_zone_ids, rule_types, created_at, updated_at) VALUES (%s, %s, %s, %s, %s, %s, UTC_TIMESTAMP(), UTC_TIMESTAMP())",
        (account_id, name, description, source_zone_id, json.dumps(target_zone_ids), json.dumps(rule_types))
    )
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE account_id = %s AND name = %s ORDER BY id DESC LIMIT 1", (account_id, name))
    return {"code": 200, "data": row, "message": "Sync rule created"}


async def _get_rule_and_context(rule_id: int):
    """Helper to load sync rule and parse JSON fields"""
    rule = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (rule_id,))
    if not rule:
        raise HTTPException(status_code=404, detail="Sync rule not found")

    import json
    source_zone_id = rule["source_zone_id"]
    target_zone_ids = json.loads(rule["target_zone_ids"]) if isinstance(rule["target_zone_ids"], str) else rule["target_zone_ids"]
    rule_types = json.loads(rule["rule_types"]) if isinstance(rule["rule_types"], str) else rule["rule_types"]
    account_id = rule["account_id"]

    from app.services.mongodb import get_db
    db = await get_db()

    return rule, source_zone_id, target_zone_ids, rule_types, account_id, db


async def _compare_and_sync(db, account_id: int, source_zone_id: str, target_zone_id: str, rule_type: str):
    """Compare source and target rules, sync to MongoDB. Returns stats."""
    source_collection_name = f"account_{account_id}_zone_{source_zone_id}_{rule_type}"
    source_rules = await db[source_collection_name].find({"account_id": str(account_id)}).to_list(length=5000)

    if not source_rules:
        return {"synced": 0, "updated": 0, "skipped": 0, "deleted": 0, "message": "No source rules found"}

    target_collection_name = f"account_{account_id}_zone_{target_zone_id}_{rule_type}"
    target_collection = db[target_collection_name]

    # Get existing target rules
    existing_rules = await target_collection.find({"zone_id": target_zone_id}).to_list(length=5000)
    existing_map = {r.get("description", ""): r for r in existing_rules if r.get("description")}

    now = datetime.utcnow()
    synced = 0
    updated = 0
    skipped = 0
    compare_priority = (rule_type == "security")

    for src_rule in source_rules:
        src_desc = src_rule.get("description", "")
        src_expr = src_rule.get("expression", "")
        src_priority = src_rule.get("priority", 0)

        if not src_desc:
            new_rule = {k: v for k, v in src_rule.items() if k not in ("_id", "synced_at")}
            new_rule["zone_id"] = target_zone_id
            new_rule["account_id"] = str(account_id)
            new_rule["synced_at"] = now
            await target_collection.insert_one(new_rule)
            synced += 1
            continue

        existing = existing_map.get(src_desc)

        if not existing:
            new_rule = {k: v for k, v in src_rule.items() if k not in ("_id", "synced_at")}
            new_rule["zone_id"] = target_zone_id
            new_rule["account_id"] = str(account_id)
            new_rule["synced_at"] = now
            await target_collection.insert_one(new_rule)
            synced += 1
            continue

        # Rule exists, compare
        existing_expr = existing.get("expression", "")
        existing_priority = existing.get("priority", 0)
        expr_match = (src_expr == existing_expr)
        priority_match = (not compare_priority) or (src_priority == existing_priority)

        if expr_match and priority_match:
            skipped += 1
            await target_collection.update_one({"_id": existing["_id"]}, {"$set": {"synced_at": now}})
        elif expr_match and not priority_match:
            await target_collection.update_one({"_id": existing["_id"]}, {"$set": {"priority": src_priority, "synced_at": now}})
            updated += 1
        elif not expr_match and priority_match:
            await target_collection.update_one({"_id": existing["_id"]}, {"$set": {"expression": src_expr, "synced_at": now}})
            updated += 1
        else:
            await target_collection.update_one({"_id": existing["_id"]}, {"$set": {"expression": src_expr, "priority": src_priority, "synced_at": now}})
            updated += 1

    # Remove stale rules
    deleted = 0
    src_descs = {r.get("description", "") for r in source_rules if r.get("description")}
    for existing in existing_rules:
        if existing.get("description") and existing["description"] not in src_descs:
            await target_collection.delete_one({"_id": existing["_id"]})
            deleted += 1

    return {"synced": synced, "updated": updated, "skipped": skipped, "deleted": deleted}


# ── Step 1: Preview (sync to MongoDB) ──

@router.put("/{rule_id}")
async def update_sync_rule(rule_id: int, body: dict):
    """Update a sync rule"""
    import json
    name = (body.get("name") or "").strip()
    description = (body.get("description") or "").strip()
    source_zone_id = body.get("sourceZoneId")
    target_zone_ids = body.get("targetZoneIds") or []
    rule_types = body.get("ruleTypes") or []

    if not name:
        raise HTTPException(status_code=400, detail="Name is required")

    row_count = await execute(
        f"UPDATE `{TABLE}` SET name=%s, description=%s, source_zone_id=%s, target_zone_ids=%s, rule_types=%s, updated_at=UTC_TIMESTAMP() WHERE id=%s",
        (name, description, source_zone_id, json.dumps(target_zone_ids), json.dumps(rule_types), rule_id)
    )
    if row_count == 0:
        raise HTTPException(status_code=404, detail="Sync rule not found")

    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (rule_id,))
    return {"code": 200, "data": row, "message": "Sync rule updated"}


# ── Step 2: Push (MongoDB → Cloudflare) ──

@router.post("/{rule_id}/push")
async def push_sync_rule(rule_id: int):
    """Step 2: Get source rules from CF and print details (dry run)"""
    rule, source_zone_id, target_zone_ids, rule_types, account_id, db = await _get_rule_and_context(rule_id)

    from app.services import cf_client

    # Get CF token from account table
    account = await query_one("SELECT api_key FROM account WHERE id = %s", (account_id,))
    if not account or not account.get("api_key"):
        return {"code": 400, "message": "No CF API token found for account"}

    token = account["api_key"]
    logger.info(f"[Push] Step 0: Got token for account {account_id}")

    results = []

    for rule_type in rule_types:
        # Step 1: Get source rules from CF
        logger.info(f"[Push] Step 1: Getting {rule_type} rules from source zone {source_zone_id}")
        try:
            if rule_type == "security":
                cf_data = await cf_client.async_list_firewall_rules(token, source_zone_id)
            elif rule_type == "ratelimit":
                cf_data = await cf_client.async_list_ratelimit_rules(token, source_zone_id)
            elif rule_type == "cache":
                cf_data = await cf_client.async_list_cache_rules(token, source_zone_id)
            elif rule_type == "ddos":
                cf_data = await cf_client.async_list_ddos_rules(token, source_zone_id)
            elif rule_type == "managed":
                cf_data = await cf_client.async_list_managed_rules(token, source_zone_id)
            elif rule_type == "dns":
                cf_data = await cf_client.async_list_dns(token, source_zone_id)
            elif rule_type == "ssl":
                ssl_data = await cf_client.async_get_ssl(token, source_zone_id)
                ssl_value = (ssl_data.get("result") or {}).get("value", "off")
                cf_data = {"success": True, "result": [{"value": ssl_value}]}
            else:
                cf_data = {"success": False, "errors": ["Unknown rule type"]}
        except Exception as e:
            logger.error(f"[Push] Failed to get source {rule_type} rules: {type(e).__name__}: {e}")
            results.append({"ruleType": rule_type, "error": str(e), "sourceRules": 0})
            continue

        source_rules = cf_data.get("result", []) if cf_data.get("success") else []
        logger.info(f"[Push] Step 1 Result: success={cf_data.get('success')}, rules={len(source_rules)}, errors={cf_data.get('errors', [])}")

        if not source_rules:
            results.append({"ruleType": rule_type, "step1": "No source rules found", "sourceRules": []})
            continue

        logger.info(f"[Push] Step 1: Source zone {source_zone_id} has {len(source_rules)} {rule_type} rules on CF")
        for r in source_rules:
            if rule_type == "dns":
                logger.info(f"[Push] Source DNS: name={r.get('name')}, type={r.get('type')}, content={r.get('content', '')[:50]}")
            elif rule_type == "ssl":
                logger.info(f"[Push] Source SSL: value={r.get('value')}")
            elif rule_type in ("ddos", "managed"):
                logger.info(f"[Push] Source Rule: desc={r.get('description')}, action={r.get('action')}")
            else:
                logger.info(f"[Push] Source Rule: desc={r.get('description')}, action={r.get('action')}, expr={r.get('expression', '')[:80]}")

        # Step 2: Get target rules from CF
        for target_zone_id in target_zone_ids:
            logger.info(f"[Push] Step 2: Getting {rule_type} rules from target zone {target_zone_id} from CF")
            try:
                if rule_type == "security":
                    target_cf_data = await cf_client.async_list_firewall_rules(token, target_zone_id)
                elif rule_type == "ratelimit":
                    target_cf_data = await cf_client.async_list_ratelimit_rules(token, target_zone_id)
                elif rule_type == "cache":
                    target_cf_data = await cf_client.async_list_cache_rules(token, target_zone_id)
                elif rule_type == "ddos":
                    target_cf_data = await cf_client.async_list_ddos_rules(token, target_zone_id)
                elif rule_type == "managed":
                    target_cf_data = await cf_client.async_list_managed_rules(token, target_zone_id)
                elif rule_type == "dns":
                    target_cf_data = await cf_client.async_list_dns(token, target_zone_id)
                elif rule_type == "ssl":
                    target_ssl_data = await cf_client.async_get_ssl(token, target_zone_id)
                    target_ssl_value = (target_ssl_data.get("result") or {}).get("value", "off")
                    target_cf_data = {"success": True, "result": [{"value": target_ssl_value}]}
                else:
                    target_cf_data = {"success": False}
            except Exception as e:
                logger.error(f"[Push] Failed to get target {rule_type} rules for zone {target_zone_id}: {type(e).__name__}: {e}")
                results.append({"targetZoneId": target_zone_id, "ruleType": rule_type, "error": str(e)})
                continue

            target_rules = target_cf_data.get("result", []) if target_cf_data.get("success") else []
            logger.info(f"[Push] Step 2 Result: target zone {target_zone_id} has {len(target_rules)} {rule_type} rules on CF")

            for r in target_rules:
                if rule_type == "dns":
                    logger.info(f"[Push] Target DNS: name={r.get('name')}, type={r.get('type')}, content={r.get('content', '')[:50]}")
                elif rule_type == "ssl":
                    logger.info(f"[Push] Target SSL: value={r.get('value')}")
                else:
                    logger.info(f"[Push] Target Rule: desc={r.get('description')}, action={r.get('action')}")

            # Step 3: Compare source vs target
            # Use description as primary key; for ddos/managed use action_parameters.id as fallback
            def _get_rule_key(r: dict) -> str:
                desc = r.get("description", "")
                if desc:
                    return desc
                # Fallback: use action_parameters.id (managed ruleset ID)
                ap = r.get("action_parameters", {})
                if ap and ap.get("id"):
                    return ap["id"]
                # Last resort: use action + position
                return f"{r.get('action', '')}_{r.get('position_index', 0)}"

            source_by_desc = {}
            for r in source_rules:
                key = _get_rule_key(r)
                source_by_desc[key] = r

            target_by_desc = {}
            target_dupes = []  # Rules with duplicate keys
            for r in target_rules:
                desc = _get_rule_key(r)
                if desc:
                    if desc in target_by_desc:
                        target_dupes.append(r)
                    else:
                        target_by_desc[desc] = r

            to_create = []
            to_update = []
            to_skip = []
            to_delete = []

            for desc, src in source_by_desc.items():
                tgt = target_by_desc.get(desc)
                if not tgt:
                    to_create.append(src)
                    logger.info(f"[Push] CREATE: {desc}")
                else:
                    src_action = src.get("action")
                    tgt_action = tgt.get("action")
                    # DDoS/Managed rules don't have expression — skip expr comparison
                    if rule_type in ("ddos", "managed"):
                        needs_update = (src_action != tgt_action)
                    else:
                        src_expr = src.get("expression", "") or (src.get("filter") or {}).get("expression", "")
                        tgt_expr = tgt.get("expression", "") or (tgt.get("filter") or {}).get("expression", "")
                        needs_update = (src_expr != tgt_expr or src_action != tgt_action)
                    # For ratelimit rules, also compare ratelimit config
                    if not needs_update and rule_type == "ratelimit":
                        src_rl = src.get("ratelimit", {})
                        tgt_rl = tgt.get("ratelimit", {})
                        if src_rl != tgt_rl:
                            needs_update = True
                    # For cache/ddos/managed rules, also compare action_parameters
                    if not needs_update and rule_type in ("cache", "ddos", "managed"):
                        src_ap = src.get("action_parameters", {})
                        tgt_ap = tgt.get("action_parameters", {})
                        if src_ap != tgt_ap:
                            needs_update = True
                    if needs_update:
                        to_update.append({"src": src, "tgt": tgt})
                        logger.info(f"[Push] UPDATE: {desc}")
                    else:
                        to_skip.append(desc)

            # Delete rules not in source
            for desc, tgt in target_by_desc.items():
                if desc not in source_by_desc:
                    to_delete.append(tgt)
                    logger.info(f"[Push] DELETE: {desc}")

            # Delete duplicate target rules (same description)
            dupe_ids = set()
            for dupe in target_dupes:
                to_delete.append(dupe)
                dupe_ids.add(dupe.get("id"))
                logger.info(f"[Push] DELETE DUPE: {dupe.get('description')} (id={dupe.get('id')})")

            logger.info(f"[Push] Step 3 Summary: create={len(to_create)}, update={len(to_update)}, skip={len(to_skip)}, delete={len(to_delete)}")

            # Clean up stale _position from source rules (leftover from previous target zone)
            for src in to_create:
                src.pop("_position", None)

            # Step 4: Push changes to target CF
            pushed_create = 0
            pushed_update = 0
            pushed_delete = 0

            if rule_type == "security":
                # Build source order map (description -> position_index)
                src_order = {r.get("description", ""): r.get("position_index") for r in source_rules if r.get("description")}

                # Delete stale/duplicate rules
                for tgt in to_delete:
                    result = cf_client.delete_firewall_rule(token, target_zone_id, tgt["id"])
                    if result.get("success"):
                        pushed_delete += 1
                        logger.info(f"[Push] Deleted security: {tgt.get('description')}")
                    else:
                        logger.error(f"[Push] Failed to delete security {tgt.get('description')}: {result}")

                # Re-fetch current rules after delete/create to get correct IDs
                # Create new rules (first rule: index=1, rest: after previous)
                last_created_id = None
                for src in to_create:
                    if not last_created_id:
                        src["_position"] = {"index": 1}
                    else:
                        src["_position"] = {"after": last_created_id}
                    try:
                        result = cf_client.create_firewall_rule(token, target_zone_id, src)
                        if result.get("success"):
                            pushed_create += 1
                            created_id = (result.get("result") or {}).get("created_rule_id") or (result.get("result") or {}).get("id")
                            if created_id:
                                last_created_id = created_id
                            logger.info(f"[Push] Created security: {src.get('description')}")
                        else:
                            logger.error(f"[Push] Failed to create security {src.get('description')}: {result}")
                    except Exception as e:
                        logger.error(f"[Push] Failed to create security {src.get('description')}: {type(e).__name__}: {e}")

                # Update existing rules (expression/action changed)
                for item in to_update:
                    tgt = item["tgt"]
                    src = item["src"]
                    try:
                        result = cf_client.update_firewall_rule(token, target_zone_id, tgt["id"], src)
                        if result.get("success"):
                            pushed_update += 1
                            logger.info(f"[Push] Updated security: {src.get('description')}")
                        else:
                            logger.error(f"[Push] Failed to update security {src.get('description')}: {result}")
                    except Exception as e:
                        logger.error(f"[Push] Failed to update security {src.get('description')}: {type(e).__name__}: {e}")

                # Reorder all rules to match source order
                source_order = [r.get("description", "") for r in source_rules if r.get("description")]
                logger.info(f"[Push] Source order: {source_order}")

                # Re-fetch current rules from CF
                current_data = cf_client.list_firewall_rules(token, target_zone_id)
                current_rules = current_data.get("result", []) if current_data.get("success") else []
                current_by_desc = {r.get("description", ""): r for r in current_rules if r.get("description")}
                target_order = [r.get("description", "") for r in current_rules if r.get("description")]
                logger.info(f"[Push] Target order: {target_order}")
                # Build ordered list from source
                source_order = [r.get("description", "") for r in source_rules if r.get("description")]
                for idx, desc in enumerate(source_order):
                    rule = current_by_desc.get(desc)
                    if not rule:
                        continue
                    if idx == 0:
                        pos = {"index": 1}
                    else:
                        prev_desc = source_order[idx - 1]
                        prev_rule = current_by_desc.get(prev_desc)
                        if prev_rule:
                            pos = {"after": prev_rule["id"]}
                        else:
                            continue
                    try:
                        import httpx
                        ruleset_id = cf_client._get_firewall_custom_ruleset_id(token, target_zone_id)
                        headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
                        url = f"https://api.cloudflare.com/client/v4/zones/{target_zone_id}/rulesets/{ruleset_id}/rules/{rule['id']}"
                        resp = httpx.patch(url, headers=headers, json={"position": pos}, timeout=30)
                        result = resp.json()
                        if result.get("success"):
                            logger.info(f"[Push] Reordered security: {desc} -> {pos}")
                        else:
                            errors = result.get("errors", [])
                            if not any("already in that position" in e.get("message", "") for e in errors):
                                logger.error(f"[Push] Failed to reorder {desc}: {errors}")
                    except Exception as e:
                        logger.error(f"[Push] Failed to reorder {desc}: {type(e).__name__}: {e}")

            elif rule_type == "ratelimit":
                # Delete stale/duplicate rules
                for tgt in to_delete:
                    result = cf_client.delete_ratelimit_rule(token, target_zone_id, tgt["id"])
                    if result.get("success"):
                        pushed_delete += 1
                        logger.info(f"[Push] Deleted ratelimit: {tgt.get('description')}")
                    else:
                        logger.error(f"[Push] Failed to delete ratelimit {tgt.get('description')}: {result}")

                # Create new rules (first: index=1, rest: after previous)
                last_created_id = None
                for src in to_create:
                    if not last_created_id:
                        src["_position"] = {"index": 1}
                    else:
                        src["_position"] = {"after": last_created_id}
                    result = cf_client.create_ratelimit_rule(token, target_zone_id, src)
                    if result.get("success"):
                        pushed_create += 1
                        created_id = (result.get("result") or {}).get("created_rule_id") or (result.get("result") or {}).get("id")
                        if created_id:
                            last_created_id = created_id
                        logger.info(f"[Push] Created ratelimit: {src.get('description')}")
                    else:
                        logger.error(f"[Push] Failed to create ratelimit {src.get('description')}: {result}")

                # Update existing rules
                for item in to_update:
                    tgt = item["tgt"]
                    src = item["src"]
                    result = cf_client.update_ratelimit_rule(token, target_zone_id, tgt["id"], src)
                    if result.get("success"):
                        pushed_update += 1
                        logger.info(f"[Push] Updated ratelimit: {src.get('description')}")
                    else:
                        logger.error(f"[Push] Failed to update ratelimit {src.get('description')}: {result}")

                # Reorder all rules to match source order
                source_order = [r.get("description", "") for r in source_rules if r.get("description")]
                logger.info(f"[Push] Ratelimit source order: {source_order}")
                current_data = cf_client.list_ratelimit_rules(token, target_zone_id)
                current_rules = current_data.get("result", []) if current_data.get("success") else []
                current_by_desc = {r.get("description", ""): r for r in current_rules if r.get("description")}
                target_order = [r.get("description", "") for r in current_rules if r.get("description")]
                logger.info(f"[Push] Ratelimit target order: {target_order}")

                ruleset_id = cf_client._get_ratelimit_ruleset_id(token, target_zone_id)
                import httpx
                headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
                for idx, desc in enumerate(source_order):
                    rule = current_by_desc.get(desc)
                    if not rule:
                        continue
                    if idx == 0:
                        pos = {"index": 1}
                    else:
                        prev_desc = source_order[idx - 1]
                        prev_rule = current_by_desc.get(prev_desc)
                        if prev_rule:
                            pos = {"after": prev_rule["id"]}
                        else:
                            continue
                    try:
                        url = f"https://api.cloudflare.com/client/v4/zones/{target_zone_id}/rulesets/{ruleset_id}/rules/{rule['id']}"
                        resp = httpx.patch(url, headers=headers, json={"position": pos}, timeout=30)
                        result = resp.json()
                        if result.get("success"):
                            logger.info(f"[Push] Reordered ratelimit: {desc} -> {pos}")
                        else:
                            errors = result.get("errors", [])
                            if not any("already in that position" in e.get("message", "") for e in errors):
                                logger.error(f"[Push] Failed to reorder ratelimit {desc}: {errors}")
                    except Exception as e:
                        logger.error(f"[Push] Failed to reorder ratelimit {desc}: {type(e).__name__}: {e}")

            elif rule_type == "cache":
                # Cache rules - same logic as security/ratelimit
                for tgt in to_delete:
                    result = cf_client.delete_cache_rule(token, target_zone_id, tgt["id"])
                    if result.get("success"):
                        pushed_delete += 1
                        logger.info(f"[Push] Deleted cache: {tgt.get('description')}")
                    else:
                        logger.error(f"[Push] Failed to delete cache {tgt.get('description')}: {result}")

                last_created_id = None
                for src in to_create:
                    if not last_created_id:
                        src["_position"] = {"index": 1}
                    else:
                        src["_position"] = {"after": last_created_id}
                    result = cf_client.create_cache_rule(token, target_zone_id, src)
                    if result.get("success"):
                        pushed_create += 1
                        created_id = (result.get("result") or {}).get("created_rule_id") or (result.get("result") or {}).get("id")
                        if created_id:
                            last_created_id = created_id
                        logger.info(f"[Push] Created cache: {src.get('description')}")
                    else:
                        logger.error(f"[Push] Failed to create cache {src.get('description')}: {result}")

                for item in to_update:
                    tgt = item["tgt"]
                    src = item["src"]
                    result = cf_client.update_cache_rule(token, target_zone_id, tgt["id"], src)
                    if result.get("success"):
                        pushed_update += 1
                        logger.info(f"[Push] Updated cache: {src.get('description')}")
                    else:
                        logger.error(f"[Push] Failed to update cache {src.get('description')}: {result}")

                # Reorder
                source_order = [r.get("description", "") for r in source_rules if r.get("description")]
                current_data = cf_client.list_cache_rules(token, target_zone_id)
                current_rules = current_data.get("result", []) if current_data.get("success") else []
                current_by_desc = {r.get("description", ""): r for r in current_rules if r.get("description")}
                ruleset_id = cf_client._get_cache_ruleset_id(token, target_zone_id)
                import httpx
                headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
                for idx, desc in enumerate(source_order):
                    rule = current_by_desc.get(desc)
                    if not rule:
                        continue
                    if idx == 0:
                        pos = {"index": 1}
                    else:
                        prev_desc = source_order[idx - 1]
                        prev_rule = current_by_desc.get(prev_desc)
                        if prev_rule:
                            pos = {"after": prev_rule["id"]}
                        else:
                            continue
                    try:
                        url = f"https://api.cloudflare.com/client/v4/zones/{target_zone_id}/rulesets/{ruleset_id}/rules/{rule['id']}"
                        resp = httpx.patch(url, headers=headers, json={"position": pos}, timeout=30)
                        result = resp.json()
                        if not result.get("success"):
                            errors = result.get("errors", [])
                            if not any("already in that position" in e.get("message", "") for e in errors):
                                logger.error(f"[Push] Failed to reorder cache {desc}: {errors}")
                    except Exception as e:
                        logger.error(f"[Push] Failed to reorder cache {desc}: {type(e).__name__}: {e}")

            elif rule_type in ("ddos", "managed"):
                # DDoS override / Managed rules
                delete_fn = cf_client.delete_ddos_rule if rule_type == "ddos" else cf_client.delete_managed_rule
                create_fn = cf_client.create_ddos_rule if rule_type == "ddos" else cf_client.create_managed_rule
                update_fn = cf_client.update_ddos_rule if rule_type == "ddos" else cf_client.update_managed_rule

                for tgt in to_delete:
                    result = delete_fn(token, target_zone_id, tgt["id"])
                    if result.get("success"):
                        pushed_delete += 1
                        logger.info(f"[Push] Deleted {rule_type}: {tgt.get('description')}")
                    else:
                        logger.error(f"[Push] Failed to delete {rule_type} {tgt.get('description')}: {result}")

                for src in to_create:
                    result = create_fn(token, target_zone_id, src)
                    if result.get("success"):
                        pushed_create += 1
                        logger.info(f"[Push] Created {rule_type}: {src.get('description')}")
                    else:
                        logger.error(f"[Push] Failed to create {rule_type} {src.get('description')}: {result}")

                for item in to_update:
                    tgt = item["tgt"]
                    src = item["src"]
                    result = update_fn(token, target_zone_id, tgt["id"], src)
                    if result.get("success"):
                        pushed_update += 1
                        logger.info(f"[Push] Updated {rule_type}: {src.get('description')}")
                    else:
                        logger.error(f"[Push] Failed to update {rule_type} {src.get('description')}: {result}")

            elif rule_type == "dns":
                # Get zone names directly from zone IDs
                src_zone_info = await cf_client.async_get_zone(token, source_zone_id)
                source_zone_name = (src_zone_info.get("result") or {}).get("name", "")
                tgt_zone_info = await cf_client.async_get_zone(token, target_zone_id)
                target_zone_name = (tgt_zone_info.get("result") or {}).get("name", "")
                logger.info(f"[Push] DNS zone names: source={source_zone_name}, target={target_zone_name}")

                # DNS records - match by name+type
                # Re-fetch target DNS to get latest state
                target_dns_data = cf_client.list_dns(token, target_zone_id)
                target_rules = target_dns_data.get("result", []) if target_dns_data.get("success") else []
                target_by_key = {(r.get("name", ""), r.get("type", "")): r for r in target_rules}

                # Build source key with zone name replacement
                # Root record (name == zone) → replace with target zone
                # Subdomain (name.endswith(.zone)) → replace zone suffix
                source_by_key = {}
                for r in source_rules:
                    rname = r.get("name", "")
                    if source_zone_name and target_zone_name and rname:
                        if rname == source_zone_name:
                            rname = target_zone_name
                        elif rname.endswith("." + source_zone_name):
                            rname = rname[:-len(source_zone_name)] + target_zone_name
                    source_by_key[(rname, r.get("type", ""))] = r

                for key, src in source_by_key.items():
                    logger.info(f"[Push] DNS lookup key={key}, found={key in target_by_key}")
                    tgt = target_by_key.get(key)
                    if not tgt:
                        # Target has no such record → create
                        create_data = {k: v for k, v in src.items() if k not in ("id", "zone_id", "zone_name", "meta", "created_on", "modified_on", "proxiable", "position_index")}
                        # Use key[0] which already has the correct zone name replacement
                        create_data["name"] = key[0]
                        logger.info(f"[Push] DNS create: name={create_data.get('name')}, type={create_data.get('type')}, content={create_data.get('content', '')[:50]}")
                        result = cf_client.create_dns(token, target_zone_id, create_data)
                        if result.get("success"):
                            pushed_create += 1
                            logger.info(f"[Push] Created DNS: {create_data.get('name')} {create_data.get('type')}")
                        else:
                            errors = result.get("errors", [])
                            if any(e.get("code") == 81058 for e in errors):
                                # Fallback: fetch fresh, find by name+type, update
                                logger.info(f"[Push] DNS 81058 fallback for {create_data.get('name')} {create_data.get('type')}")
                                fresh = cf_client.list_dns(token, target_zone_id)
                                for rec in (fresh.get("result", []) if fresh.get("success") else []):
                                    if rec.get("name") == create_data.get("name") and rec.get("type") == create_data.get("type"):
                                        src_c = create_data.get("content", "")
                                        tgt_c = rec.get("content", "")
                                        if src_c != tgt_c or create_data.get("ttl", 1) != rec.get("ttl", 1) or create_data.get("proxied", False) != rec.get("proxied", False):
                                            ur = cf_client.update_dns(token, target_zone_id, rec["id"], create_data)
                                            if ur.get("success"):
                                                pushed_update += 1
                                                logger.info(f"[Push] Updated DNS (81058): {create_data.get('name')} ({tgt_c} -> {src_c})")
                                            else:
                                                logger.error(f"[Push] Failed update DNS (81058): {ur}")
                                        else:
                                            logger.info(f"[Push] DNS identical (81058 skip): {create_data.get('name')}")
                                        break
                            else:
                                logger.error(f"[Push] Failed to create DNS {create_data.get('name')}: {result}")
                    else:
                        # Target has record → compare content/ttl/proxied
                        src_content = src.get("content", "")
                        tgt_content = tgt.get("content", "")
                        src_ttl = src.get("ttl", 1)
                        tgt_ttl = tgt.get("ttl", 1)
                        src_proxied = src.get("proxied", False)
                        tgt_proxied = tgt.get("proxied", False)
                        if src_content != tgt_content or src_ttl != tgt_ttl or src_proxied != tgt_proxied:
                            update_data = {"content": src_content, "ttl": src_ttl, "proxied": src_proxied, "name": src.get("name"), "type": src.get("type")}
                            result = cf_client.update_dns(token, target_zone_id, tgt["id"], update_data)
                            if result.get("success"):
                                pushed_update += 1
                                logger.info(f"[Push] Updated DNS: {src.get('name')} {src.get('type')} ({tgt_content} -> {src_content})")
                            else:
                                logger.error(f"[Push] Failed to update DNS {src.get('name')}: {result}")
                        else:
                            to_skip.append(f"{key[0]} {key[1]}")

                for key, tgt in target_by_key.items():
                    if key not in source_by_key:
                        result = cf_client.delete_dns(token, target_zone_id, tgt["id"])
                        if result.get("success"):
                            pushed_delete += 1
                            logger.info(f"[Push] Deleted DNS: {tgt.get('name')} {tgt.get('type')}")
                        else:
                            logger.error(f"[Push] Failed to delete DNS {tgt.get('name')}: {result}")

            elif rule_type == "ssl":
                # SSL is a single zone setting
                source_ssl = source_rules[0] if source_rules else {}
                target_ssl = target_rules[0] if target_rules else {}
                src_value = source_ssl.get("value", "off")
                tgt_value = target_ssl.get("value", "off")
                if src_value != tgt_value:
                    result = cf_client.update_ssl(token, target_zone_id, src_value)
                    if result.get("success"):
                        pushed_update += 1
                        logger.info(f"[Push] Updated SSL: {tgt_value} -> {src_value}")
                    else:
                        logger.error(f"[Push] Failed to update SSL: {result}")
                else:
                    to_skip.append("ssl")

            logger.info(f"[Push] Step 4 Done: created={pushed_create}, updated={pushed_update}, deleted={pushed_delete}")

            results.append({
                "targetZoneId": target_zone_id,
                "ruleType": rule_type,
                "sourceRules": len(source_rules),
                "targetRules": len(target_rules),
                "toCreate": len(to_create),
                "toUpdate": len(to_update),
                "toSkip": len(to_skip),
                "toDelete": len(to_delete),
                "pushedCreate": pushed_create,
                "pushedUpdate": pushed_update,
                "pushedDelete": pushed_delete,
            })

    # Update last_synced_at
    await execute(f"UPDATE `{TABLE}` SET last_synced_at=UTC_TIMESTAMP() WHERE id=%s", (rule_id,))

    return {
        "code": 200,
        "data": {"results": results},
        "message": "Dry run completed, check logs for details",
    }


@router.delete("/{rule_id}")
async def delete_sync_rule(rule_id: int):
    """Delete a sync rule"""
    row_count = await execute(f"DELETE FROM `{TABLE}` WHERE id = %s", (rule_id,))
    if row_count == 0:
        raise HTTPException(status_code=404, detail="Sync rule not found")
    return {"code": 200, "data": None, "message": "Sync rule deleted"}
