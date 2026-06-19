import logging
import asyncio
from cloudflare import Cloudflare

logger = logging.getLogger(__name__)


def get_client(api_token: str) -> Cloudflare:
    return Cloudflare(api_token=api_token)


def _auto_paginate(paginated_result):
    """Iterate all pages of a paginated CF result."""
    for item in paginated_result:
        yield item


# ── Sync functions ──

def list_zones(api_token: str, per_page: int = 50, account_id: str = None) -> dict:
    cf = get_client(api_token)
    if account_id:
        resp = cf.zones.list(per_page=per_page, account={"id": account_id})
    else:
        resp = cf.zones.list(per_page=per_page)
    result = [z.model_dump() for z in _auto_paginate(resp)]
    return {"success": True, "result": result, "result_info": {}}


def get_zone(api_token: str, zone_id: str) -> dict:
    cf = get_client(api_token)
    resp = cf.zones.get(zone_id=zone_id)
    return {"success": True, "result": resp.model_dump()}


def list_dns(api_token: str, zone_id: str, per_page: int = 100) -> dict:
    cf = get_client(api_token)
    resp = cf.dns.records.list(zone_id=zone_id, per_page=per_page)
    result = [r.model_dump() for r in _auto_paginate(resp)]
    return {"success": True, "result": result, "result_info": {}}


def _filter_dns_fields(data: dict) -> dict:
    """Filter out read-only fields from DNS record data."""
    readonly = {
        "id", "zone_id", "zone_name", "meta", "created_on", "modified_on",
        "proxiable", "comment_modified_on", "comment_modified_by",
        "tags_modified_on", "tags_modified_by", "tags",
    }
    return {k: v for k, v in data.items() if k not in readonly}


def create_dns(api_token: str, zone_id: str, data: dict) -> dict:
    cf = get_client(api_token)
    filtered = _filter_dns_fields(data)
    resp = cf.dns.records.create(zone_id=zone_id, **filtered)
    return {"success": True, "result": resp.model_dump()}


def update_dns(api_token: str, zone_id: str, record_id: str, data: dict) -> dict:
    cf = get_client(api_token)
    filtered = _filter_dns_fields(data)
    resp = cf.dns.records.update(dns_record_id=record_id, zone_id=zone_id, **filtered)
    return {"success": True, "result": resp.model_dump()}


def delete_dns(api_token: str, zone_id: str, record_id: str) -> dict:
    cf = get_client(api_token)
    cf.dns.records.delete(dns_record_id=record_id, zone_id=zone_id)
    return {"success": True, "result": None}


def _get_firewall_custom_ruleset_id(api_token: str, zone_id: str) -> str:
    """Find the http_request_firewall_custom ruleset for a zone."""
    cf = get_client(api_token)
    resp = cf.rulesets.list(zone_id=zone_id)
    for r in resp:
        r_dict = r.model_dump()
        if r_dict.get("phase") == "http_request_firewall_custom":
            return r_dict["id"]
    return ""


def list_firewall_rules(api_token: str, zone_id: str) -> dict:
    """List security rules via Rulesets API (IDs match create/update/delete)."""
    cf = get_client(api_token)
    ruleset_id = _get_firewall_custom_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        return {"success": True, "result": []}
    detail = cf.rulesets.get(ruleset_id=ruleset_id, zone_id=zone_id)
    rules = []
    for idx, rule in enumerate(detail.rules or []):
        r = rule.model_dump()
        rules.append({
            "id": r.get("id", ""),
            "action": r.get("action", "block"),
            "description": r.get("description", ""),
            "expression": r.get("expression", ""),
            "enabled": r.get("enabled", True),
            "position_index": idx + 1,  # 1-based position in ruleset
        })
    return {"success": True, "result": rules}


def create_firewall_rule(api_token: str, zone_id: str, data: dict) -> dict:
    """Create a security rule via Rulesets API."""
    cf = get_client(api_token)
    ruleset_id = _get_firewall_custom_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        return {"success": False, "errors": [{"message": "No http_request_firewall_custom ruleset found"}]}

    action = data.get("action", "block")
    expression = data.get("expression", "") or (data.get("filter") or {}).get("expression", "")
    description = data.get("description", "")
    enabled = not data.get("paused", False)
    priority = data.get("priority")

    kwargs = {
        "ruleset_id": ruleset_id,
        "zone_id": zone_id,
        "action": action,
        "expression": expression,
        "description": description,
        "enabled": enabled,
    }
    if action == "skip":
        kwargs["action_parameters"] = {"ruleset": "current"}

    # Handle position
    position = data.get("_position")
    if position:
        kwargs["position"] = position

    logger.info(f"[CF] Create rule via Rulesets: action={action}, desc={description}")
    resp = cf.rulesets.rules.create(**kwargs)
    return {"success": True, "result": resp.model_dump() if hasattr(resp, 'model_dump') else {}}


def update_firewall_rule(api_token: str, zone_id: str, rule_id: str, data: dict) -> dict:
    """Update a security rule via Rulesets API."""
    cf = get_client(api_token)
    ruleset_id = _get_firewall_custom_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        return {"success": False, "errors": [{"message": "No http_request_firewall_custom ruleset found"}]}

    action = data.get("action", "block")
    expression = data.get("expression", "") or (data.get("filter") or {}).get("expression", "")
    description = data.get("description", "")
    enabled = not data.get("paused", False)
    priority = data.get("priority")

    kwargs = {
        "rule_id": rule_id,
        "ruleset_id": ruleset_id,
        "zone_id": zone_id,
        "action": action,
        "expression": expression,
        "description": description,
        "enabled": enabled,
    }
    if action == "skip":
        kwargs["action_parameters"] = {"ruleset": "current"}

    # Handle position
    position = data.get("_position")
    if position:
        kwargs["position"] = position

    logger.info(f"[CF] Update rule via Rulesets: id={rule_id}, action={action}, desc={description}")
    resp = cf.rulesets.rules.edit(**kwargs)
    return {"success": True, "result": resp.model_dump() if hasattr(resp, 'model_dump') else {}}


def delete_firewall_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    """Delete a security rule via Rulesets API."""
    cf = get_client(api_token)
    ruleset_id = _get_firewall_custom_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        return {"success": False, "errors": [{"message": "No http_request_firewall_custom ruleset found"}]}
    resp = cf.rulesets.rules.delete(rule_id=rule_id, ruleset_id=ruleset_id, zone_id=zone_id)
    return {"success": True, "result": resp.model_dump() if hasattr(resp, 'model_dump') else {}}



def get_ssl(api_token: str, zone_id: str) -> dict:
    cf = get_client(api_token)
    resp = cf.zones.settings.get(zone_id=zone_id, setting_id="ssl")
    return {"success": True, "result": resp.model_dump()}


def update_ssl(api_token: str, zone_id: str, value: str) -> dict:
    cf = get_client(api_token)
    resp = cf.zones.settings.edit(zone_id=zone_id, setting_id="ssl", value=value)
    return {"success": True, "result": resp.model_dump()}


def purge_all(api_token: str, zone_id: str) -> dict:
    cf = get_client(api_token)
    resp = cf.cache.purge(zone_id=zone_id, purge_everything=True)
    return {"success": True, "result": resp.model_dump() if resp else {"purged": True}}


def purge_by_urls(api_token: str, zone_id: str, files: list) -> dict:
    cf = get_client(api_token)
    resp = cf.cache.purge(zone_id=zone_id, files=files)
    return {"success": True, "result": resp.model_dump() if resp else {"purged": len(files)}}


def purge_by_tags(api_token: str, zone_id: str, tags: list) -> dict:
    cf = get_client(api_token)
    resp = cf.cache.purge(zone_id=zone_id, tags=tags)
    return {"success": True, "result": resp.model_dump() if resp else {"purged": len(tags)}}


def purge_by_hosts(api_token: str, zone_id: str, hosts: list) -> dict:
    cf = get_client(api_token)
    resp = cf.cache.purge(zone_id=zone_id, hosts=hosts)
    return {"success": True, "result": resp.model_dump() if resp else {"purged": len(hosts)}}


def purge_by_prefixes(api_token: str, zone_id: str, prefixes: list) -> dict:
    cf = get_client(api_token)
    resp = cf.cache.purge(zone_id=zone_id, prefixes=prefixes)
    return {"success": True, "result": resp.model_dump() if resp else {"purged": len(prefixes)}}


def _get_cache_ruleset_id(api_token: str, zone_id: str) -> str:
    """Find the http_request_cache_settings ruleset for a zone."""
    cf = get_client(api_token)
    resp = cf.rulesets.list(zone_id=zone_id)
    for r in resp:
        r_dict = r.model_dump()
        if r_dict.get("phase") == "http_request_cache_settings":
            return r_dict["id"]
    return ""


def list_cache_rules(api_token: str, zone_id: str) -> dict:
    """Fetch cache rules via Rulesets API (phase: http_request_cache_settings)."""
    cf = get_client(api_token)
    ruleset_id = _get_cache_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        return {"success": True, "result": []}
    detail = cf.rulesets.get(ruleset_id=ruleset_id, zone_id=zone_id)
    rules = []
    for idx, rule in enumerate(detail.rules or []):
        r = rule.model_dump()
        r["position_index"] = idx + 1
        rules.append(r)
    return {"success": True, "result": rules}


def create_cache_rule(api_token: str, zone_id: str, data: dict) -> dict:
    """Create a cache rule via Rulesets API."""
    cf = get_client(api_token)
    ruleset_id = _get_cache_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        logger.info(f"[CF] Creating http_request_cache_settings ruleset for zone {zone_id}")
        try:
            resp = cf.rulesets.create(
                zone_id=zone_id,
                kind="zone",
                name="Cache rules",
                phase="http_request_cache_settings",
            )
            ruleset_id = resp.id if hasattr(resp, 'id') else resp.model_dump().get("id", "")
        except Exception as e:
            logger.error(f"[CF] Failed to create cache ruleset: {e}")
            return {"success": False, "errors": [{"message": f"Failed to create ruleset: {e}"}]}
    if not ruleset_id:
        return {"success": False, "errors": [{"message": "No http_request_cache_settings ruleset found"}]}
    kwargs = {
        "ruleset_id": ruleset_id,
        "zone_id": zone_id,
        "action": data.get("action", "block"),
        "expression": data.get("expression", ""),
        "description": data.get("description", ""),
        "enabled": not data.get("paused", False),
    }
    action_params = data.get("action_parameters")
    if action_params:
        kwargs["action_parameters"] = action_params
    position = data.get("_position")
    if position:
        kwargs["position"] = position
    logger.info(f"[CF] Create cache rule: action={kwargs['action']}, desc={kwargs['description']}")
    resp = cf.rulesets.rules.create(**kwargs)
    result = resp.model_dump() if hasattr(resp, 'model_dump') else {}
    rules = result.get("rules", [])
    if rules:
        result["created_rule_id"] = rules[-1].get("id", "")
    return {"success": True, "result": result}


def update_cache_rule(api_token: str, zone_id: str, rule_id: str, data: dict) -> dict:
    """Update a cache rule via Rulesets API."""
    cf = get_client(api_token)
    ruleset_id = _get_cache_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        return {"success": False, "errors": [{"message": "No http_request_cache_settings ruleset found"}]}
    kwargs = {
        "rule_id": rule_id,
        "ruleset_id": ruleset_id,
        "zone_id": zone_id,
        "action": data.get("action", "block"),
        "expression": data.get("expression", ""),
        "description": data.get("description", ""),
        "enabled": not data.get("paused", False),
    }
    action_params = data.get("action_parameters")
    if action_params:
        kwargs["action_parameters"] = action_params
    position = data.get("_position")
    if position:
        kwargs["position"] = position
    logger.info(f"[CF] Update cache rule: id={rule_id}, action={kwargs['action']}")
    resp = cf.rulesets.rules.edit(**kwargs)
    return {"success": True, "result": resp.model_dump() if hasattr(resp, 'model_dump') else {}}


def delete_cache_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    """Delete a cache rule via Rulesets API."""
    cf = get_client(api_token)
    ruleset_id = _get_cache_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        return {"success": False, "errors": [{"message": "No http_request_cache_settings ruleset found"}]}
    resp = cf.rulesets.rules.delete(rule_id=rule_id, ruleset_id=ruleset_id, zone_id=zone_id)
    return {"success": True, "result": resp.model_dump() if hasattr(resp, 'model_dump') else {}}


# ── Async wrappers (non-blocking, runs sync code in thread pool) ──

async def async_list_zones(api_token: str, per_page: int = 50, account_id: str = None) -> dict:
    return await asyncio.to_thread(list_zones, api_token, per_page, account_id)


def verify_token(api_token: str) -> dict:
    cf = get_client(api_token)
    # CF API 没有直接的 token verify，用 user.ips 间接测试
    try:
        resp = cf.user.ips.list()
        return {"success": True, "result": {"status": "active"}}
    except Exception as e:
        return {"success": False, "errors": [{"message": str(e)}]}


async def async_verify_token(api_token: str) -> dict:
    return await asyncio.to_thread(verify_token, api_token)

async def async_get_zone(api_token: str, zone_id: str) -> dict:
    return await asyncio.to_thread(get_zone, api_token, zone_id)

async def async_list_dns(api_token: str, zone_id: str, per_page: int = 100) -> dict:
    return await asyncio.to_thread(list_dns, api_token, zone_id, per_page)

async def async_create_dns(api_token: str, zone_id: str, data: dict) -> dict:
    return await asyncio.to_thread(create_dns, api_token, zone_id, data)

async def async_update_dns(api_token: str, zone_id: str, record_id: str, data: dict) -> dict:
    return await asyncio.to_thread(update_dns, api_token, zone_id, record_id, data)

async def async_delete_dns(api_token: str, zone_id: str, record_id: str) -> dict:
    return await asyncio.to_thread(delete_dns, api_token, zone_id, record_id)

async def async_list_firewall_rules(api_token: str, zone_id: str) -> dict:
    return await asyncio.to_thread(list_firewall_rules, api_token, zone_id)

async def async_list_firewall_filters(api_token: str, zone_id: str) -> dict:
    return await asyncio.to_thread(list_firewall_filters, api_token, zone_id)

def create_firewall_filter(api_token: str, zone_id: str, data: dict) -> dict:
    cf = get_client(api_token)
    resp = cf.filters.create(zone_id=zone_id, body=[data])
    result = [f.model_dump() for f in resp]
    return {"success": True, "result": result[0] if result else {}}

async def async_create_firewall_filter(api_token: str, zone_id: str, data: dict) -> dict:
    return await asyncio.to_thread(create_firewall_filter, api_token, zone_id, data)

async def async_create_firewall_rule(api_token: str, zone_id: str, data: dict) -> dict:
    return await asyncio.to_thread(create_firewall_rule, api_token, zone_id, data)

async def async_get_firewall_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    return await asyncio.to_thread(get_firewall_rule, api_token, zone_id, rule_id)

async def async_update_firewall_rule(api_token: str, zone_id: str, rule_id: str, data: dict) -> dict:
    return await asyncio.to_thread(update_firewall_rule, api_token, zone_id, rule_id, data)

async def async_delete_firewall_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    return await asyncio.to_thread(delete_firewall_rule, api_token, zone_id, rule_id)

async def async_get_ssl(api_token: str, zone_id: str) -> dict:
    return await asyncio.to_thread(get_ssl, api_token, zone_id)

async def async_update_ssl(api_token: str, zone_id: str, value: str) -> dict:
    return await asyncio.to_thread(update_ssl, api_token, zone_id, value)

async def async_purge_all(api_token: str, zone_id: str) -> dict:
    return await asyncio.to_thread(purge_all, api_token, zone_id)

async def async_purge_by_urls(api_token: str, zone_id: str, files: list) -> dict:
    return await asyncio.to_thread(purge_by_urls, api_token, zone_id, files)

async def async_purge_by_tags(api_token: str, zone_id: str, tags: list) -> dict:
    return await asyncio.to_thread(purge_by_tags, api_token, zone_id, tags)

async def async_purge_by_hosts(api_token: str, zone_id: str, hosts: list) -> dict:
    return await asyncio.to_thread(purge_by_hosts, api_token, zone_id, hosts)

async def async_purge_by_prefixes(api_token: str, zone_id: str, prefixes: list) -> dict:
    return await asyncio.to_thread(purge_by_prefixes, api_token, zone_id, prefixes)

async def async_list_cache_rules(api_token: str, zone_id: str) -> dict:
    return await asyncio.to_thread(list_cache_rules, api_token, zone_id)

def list_ratelimit_rules(api_token: str, zone_id: str) -> dict:
    """Fetch rate limit rules via Cloudflare Rulesets API (phase: http_ratelimit)."""
    cf = get_client(api_token)
    resp = cf.rulesets.list(zone_id=zone_id)
    ratelimit_ruleset = None
    for r in resp:
        r_dict = r.model_dump()
        if r_dict.get("phase") == "http_ratelimit":
            ratelimit_ruleset = r_dict
            break
    if not ratelimit_ruleset:
        return {"success": True, "result": []}

    ruleset_id = ratelimit_ruleset["id"]
    detail = cf.rulesets.get(ruleset_id=ruleset_id, zone_id=zone_id)
    rules = []
    for rule in (detail.rules or []):
        r = rule.model_dump()
        # Keep full ratelimit config
        ratelimit = r.get("ratelimit") or {}
        rules.append({
            "id": r.get("id", ""),
            "action": r.get("action", "block"),
            "description": r.get("description", ""),
            "expression": r.get("expression", ""),
            "enabled": r.get("enabled", True),
            "ratelimit": ratelimit,
        })
    return {"success": True, "result": rules}

async def async_list_ratelimit_rules(api_token: str, zone_id: str) -> dict:
    return await asyncio.to_thread(list_ratelimit_rules, api_token, zone_id)


def _get_ratelimit_ruleset_id(api_token: str, zone_id: str) -> str:
    """Find the http_ratelimit ruleset for a zone."""
    cf = get_client(api_token)
    resp = cf.rulesets.list(zone_id=zone_id)
    for r in resp:
        r_dict = r.model_dump()
        if r_dict.get("phase") == "http_ratelimit":
            return r_dict["id"]
    return ""


def create_ratelimit_rule(api_token: str, zone_id: str, data: dict) -> dict:
    """Create a rate limit rule via Rulesets API."""
    cf = get_client(api_token)
    ruleset_id = _get_ratelimit_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        # Create the ruleset if it doesn't exist
        logger.info(f"[CF] Creating http_ratelimit ruleset for zone {zone_id}")
        try:
            resp = cf.rulesets.create(
                zone_id=zone_id,
                kind="zone",
                name="Rate limiting rules",
                phase="http_ratelimit",
            )
            ruleset_id = resp.id if hasattr(resp, 'id') else resp.model_dump().get("id", "")
            logger.info(f"[CF] Created http_ratelimit ruleset: {ruleset_id}")
        except Exception as e:
            logger.error(f"[CF] Failed to create http_ratelimit ruleset: {e}")
            return {"success": False, "errors": [{"message": f"Failed to create ruleset: {e}"}]}
    if not ruleset_id:
        return {"success": False, "errors": [{"message": "No http_ratelimit ruleset found"}]}
    kwargs = {
        "ruleset_id": ruleset_id,
        "zone_id": zone_id,
        "action": data.get("action", "block"),
        "expression": data.get("expression", ""),
        "description": data.get("description", ""),
        "enabled": not data.get("paused", False),
    }
    # Use full ratelimit config from source if available
    ratelimit = data.get("ratelimit") or {
        "characteristics": ["ip.src"],
        "requests_to_origin": False,
    }
    if "characteristics" not in ratelimit:
        ratelimit["characteristics"] = ["ip.src"]
    kwargs["ratelimit"] = ratelimit
    # Handle position
    position = data.get("_position")
    if position:
        kwargs["position"] = position
    logger.info(f"[CF] Create ratelimit rule: action={kwargs['action']}, desc={kwargs['description']}")
    resp = cf.rulesets.rules.create(**kwargs)
    result = resp.model_dump() if hasattr(resp, 'model_dump') else {}
    rules = result.get("rules", [])
    if rules:
        result["created_rule_id"] = rules[-1].get("id", "")
    return {"success": True, "result": result}


def update_ratelimit_rule(api_token: str, zone_id: str, rule_id: str, data: dict) -> dict:
    """Update a rate limit rule via Rulesets API."""
    cf = get_client(api_token)
    ruleset_id = _get_ratelimit_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        return {"success": False, "errors": [{"message": "No http_ratelimit ruleset found"}]}
    kwargs = {
        "rule_id": rule_id,
        "ruleset_id": ruleset_id,
        "zone_id": zone_id,
        "action": data.get("action", "block"),
        "expression": data.get("expression", ""),
        "description": data.get("description", ""),
        "enabled": not data.get("paused", False),
    }
    ratelimit = data.get("ratelimit") or {
        "characteristics": ["ip.src"],
        "requests_to_origin": False,
    }
    if "characteristics" not in ratelimit:
        ratelimit["characteristics"] = ["ip.src"]
    if data.get("mitigation_timeout"):
        ratelimit["mitigation_timeout"] = int(data["mitigation_timeout"])
    # If only updating position, use httpx to avoid SDK validation
    position = data.get("_position")
    if position and not data.get("expression"):
        import httpx
        headers = {"Authorization": f"Bearer {api_token}", "Content-Type": "application/json"}
        url = f"https://api.cloudflare.com/client/v4/zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id}"
        payload = {"position": position}
        logger.info(f"[CF] Reorder ratelimit rule: id={rule_id}, position={position}")
        resp = httpx.patch(url, headers=headers, json=payload, timeout=30)
        return resp.json()

    if ratelimit:
        kwargs["ratelimit"] = ratelimit
    if position:
        kwargs["position"] = position
    logger.info(f"[CF] Update ratelimit rule: id={rule_id}, action={kwargs['action']}")
    resp = cf.rulesets.rules.edit(**kwargs)
    return {"success": True, "result": resp.model_dump() if hasattr(resp, 'model_dump') else {}}


def delete_ratelimit_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    """Delete a rate limit rule via Rulesets API."""
    cf = get_client(api_token)
    ruleset_id = _get_ratelimit_ruleset_id(api_token, zone_id)
    if not ruleset_id:
        return {"success": False, "errors": [{"message": "No http_ratelimit ruleset found"}]}
    resp = cf.rulesets.rules.delete(rule_id=rule_id, ruleset_id=ruleset_id, zone_id=zone_id)
    return {"success": True, "result": resp.model_dump() if hasattr(resp, 'model_dump') else {}}


async def async_get_client(api_token: str) -> Cloudflare:
    return await asyncio.to_thread(get_client, api_token)


# ── DDoS L7 override rules (phase: ddos_l7 via rulesets.phases) ──

def list_ddos_rules(api_token: str, zone_id: str) -> dict:
    """Get DDoS override rules via phases.get (entrypoint ruleset)."""
    cf = get_client(api_token)
    try:
        resp = cf.rulesets.phases.get(ruleset_phase="ddos_l7", zone_id=zone_id)
        result = resp.model_dump() if hasattr(resp, 'model_dump') else {}
        rules = result.get("rules", [])
        for idx, r in enumerate(rules):
            r["position_index"] = idx + 1
        return {"success": True, "result": rules, "ruleset_id": result.get("id", "")}
    except Exception as e:
        logger.info(f"[CF] No DDoS entrypoint for zone {zone_id}: {e}")
        return {"success": True, "result": []}


def _update_ddos_rules(api_token: str, zone_id: str, rules: list) -> dict:
    """Update ALL DDoS override rules at once via phases.update."""
    cf = get_client(api_token)
    try:
        resp = cf.rulesets.phases.update(
            ruleset_phase="ddos_l7",
            zone_id=zone_id,
            rules=rules,
        )
        result = resp.model_dump() if hasattr(resp, 'model_dump') else {}
        return {"success": True, "result": result}
    except Exception as e:
        err_str = str(e)
        # 404 = no entrypoint yet, create it then retry
        if "10003" in err_str or "could not find entrypoint" in err_str:
            logger.info(f"[CF] Creating DDoS entrypoint for zone {zone_id} with {len(rules)} rules")
            try:
                resp = cf.rulesets.create(
                    zone_id=zone_id,
                    kind="zone",
                    name="DDoS L7 rules",
                    phase="ddos_l7",
                    rules=rules,
                )
                result = resp.model_dump() if hasattr(resp, 'model_dump') else {}
                return {"success": True, "result": result}
            except Exception as e2:
                logger.error(f"[CF] Failed to create DDoS entrypoint: {type(e2).__name__}: {e2}")
                return {"success": False, "errors": [{"message": f"Failed to create DDoS entrypoint: {e2}. Check API token has DDoS write permission."}]}
        logger.error(f"[CF] Failed to update DDoS overrides: {e}")
        return {"success": False, "errors": [{"message": err_str}]}


def create_ddos_rule(api_token: str, zone_id: str, data: dict) -> dict:
    """Add a DDoS override rule by appending to existing rules."""
    current = list_ddos_rules(api_token, zone_id)
    existing_rules = current.get("result", []) if current.get("success") else []
    # Strip CF metadata, keep only rule fields
    clean_rules = []
    for r in existing_rules:
        clean = {k: v for k, v in r.items() if k not in ("id", "position_index", "last_updated")}
        clean_rules.append(clean)
    new_rule = {
        "action": data.get("action", "block"),
        "expression": data.get("expression", "true"),
        "description": data.get("description", ""),
        "enabled": not data.get("paused", False),
    }
    action_params = data.get("action_parameters")
    if action_params:
        new_rule["action_parameters"] = action_params
    clean_rules.append(new_rule)
    logger.info(f"[CF] Create DDoS override: desc={new_rule['description']}")
    return _update_ddos_rules(api_token, zone_id, clean_rules)


def update_ddos_rule(api_token: str, zone_id: str, rule_id: str, data: dict) -> dict:
    """Update a DDoS override rule by replacing it in the rules list."""
    current = list_ddos_rules(api_token, zone_id)
    existing_rules = current.get("result", []) if current.get("success") else []
    clean_rules = []
    found = False
    for r in existing_rules:
        if r.get("id") == rule_id:
            found = True
            updated = {
                "action": data.get("action", r.get("action", "block")),
                "expression": data.get("expression", r.get("expression", "true")),
                "description": data.get("description", r.get("description", "")),
                "enabled": not data.get("paused", False) if "paused" in data else r.get("enabled", True),
            }
            action_params = data.get("action_parameters")
            if action_params:
                updated["action_parameters"] = action_params
            elif r.get("action_parameters"):
                updated["action_parameters"] = r["action_parameters"]
            clean_rules.append(updated)
        else:
            clean = {k: v for k, v in r.items() if k not in ("id", "position_index", "last_updated")}
            clean_rules.append(clean)
    if not found:
        return {"success": False, "errors": [{"message": f"Rule {rule_id} not found"}]}
    logger.info(f"[CF] Update DDoS override: id={rule_id}")
    return _update_ddos_rules(api_token, zone_id, clean_rules)


def delete_ddos_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    """Delete a DDoS override rule by removing from rules list."""
    current = list_ddos_rules(api_token, zone_id)
    existing_rules = current.get("result", []) if current.get("success") else []
    clean_rules = []
    for r in existing_rules:
        if r.get("id") != rule_id:
            clean = {k: v for k, v in r.items() if k not in ("id", "position_index", "last_updated")}
            clean_rules.append(clean)
    logger.info(f"[CF] Delete DDoS override: id={rule_id}")
    return _update_ddos_rules(api_token, zone_id, clean_rules)


async def async_list_ddos_rules(api_token: str, zone_id: str) -> dict:
    return await asyncio.to_thread(list_ddos_rules, api_token, zone_id)


async def async_create_ddos_rule(api_token: str, zone_id: str, data: dict) -> dict:
    return await asyncio.to_thread(create_ddos_rule, api_token, zone_id, data)


async def async_update_ddos_rule(api_token: str, zone_id: str, rule_id: str, data: dict) -> dict:
    return await asyncio.to_thread(update_ddos_rule, api_token, zone_id, rule_id, data)


async def async_delete_ddos_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    return await asyncio.to_thread(delete_ddos_rule, api_token, zone_id, rule_id)


# ── Managed Rules (phase: http_request_firewall_managed via rulesets.phases) ──

def list_managed_rules(api_token: str, zone_id: str) -> dict:
    """Get managed rules via phases.get (entrypoint ruleset)."""
    cf = get_client(api_token)
    try:
        resp = cf.rulesets.phases.get(ruleset_phase="http_request_firewall_managed", zone_id=zone_id)
        result = resp.model_dump() if hasattr(resp, 'model_dump') else {}
        rules = result.get("rules", [])
        for idx, r in enumerate(rules):
            r["position_index"] = idx + 1
        return {"success": True, "result": rules, "ruleset_id": result.get("id", "")}
    except Exception as e:
        logger.info(f"[CF] No managed entrypoint for zone {zone_id}: {e}")
        return {"success": True, "result": []}


def _update_managed_rules(api_token: str, zone_id: str, rules: list) -> dict:
    """Update ALL managed rules at once via phases.update."""
    cf = get_client(api_token)
    try:
        resp = cf.rulesets.phases.update(
            ruleset_phase="http_request_firewall_managed",
            zone_id=zone_id,
            rules=rules,
        )
        result = resp.model_dump() if hasattr(resp, 'model_dump') else {}
        return {"success": True, "result": result}
    except Exception as e:
        err_str = str(e)
        if "10003" in err_str or "could not find entrypoint" in err_str:
            logger.info(f"[CF] Creating managed entrypoint for zone {zone_id} with {len(rules)} rules")
            try:
                resp = cf.rulesets.create(
                    zone_id=zone_id,
                    kind="zone",
                    name="Managed rules",
                    phase="http_request_firewall_managed",
                    rules=rules,
                )
                result = resp.model_dump() if hasattr(resp, 'model_dump') else {}
                return {"success": True, "result": result}
            except Exception as e2:
                logger.error(f"[CF] Failed to create managed entrypoint: {type(e2).__name__}: {e2}")
                return {"success": False, "errors": [{"message": f"Failed to create managed entrypoint: {e2}. Check API token has WAF write permission."}]}
        logger.error(f"[CF] Failed to update managed rules: {e}")
        return {"success": False, "errors": [{"message": err_str}]}


def create_managed_rule(api_token: str, zone_id: str, data: dict) -> dict:
    """Add a managed rule by appending to existing rules."""
    current = list_managed_rules(api_token, zone_id)
    existing_rules = current.get("result", []) if current.get("success") else []
    clean_rules = []
    for r in existing_rules:
        clean = {k: v for k, v in r.items() if k not in ("id", "position_index", "last_updated")}
        clean_rules.append(clean)
    new_rule = {
        "action": data.get("action", "block"),
        "expression": data.get("expression", "true"),
        "description": data.get("description", ""),
        "enabled": not data.get("paused", False),
    }
    action_params = data.get("action_parameters")
    if action_params:
        new_rule["action_parameters"] = action_params
    clean_rules.append(new_rule)
    logger.info(f"[CF] Create managed rule: desc={new_rule['description']}")
    return _update_managed_rules(api_token, zone_id, clean_rules)


def update_managed_rule(api_token: str, zone_id: str, rule_id: str, data: dict) -> dict:
    """Update a managed rule by replacing it in the rules list."""
    current = list_managed_rules(api_token, zone_id)
    existing_rules = current.get("result", []) if current.get("success") else []
    clean_rules = []
    found = False
    for r in existing_rules:
        if r.get("id") == rule_id:
            found = True
            updated = {
                "action": data.get("action", r.get("action", "block")),
                "expression": data.get("expression", r.get("expression", "true")),
                "description": data.get("description", r.get("description", "")),
                "enabled": not data.get("paused", False) if "paused" in data else r.get("enabled", True),
            }
            action_params = data.get("action_parameters")
            if action_params:
                updated["action_parameters"] = action_params
            elif r.get("action_parameters"):
                updated["action_parameters"] = r["action_parameters"]
            clean_rules.append(updated)
        else:
            clean = {k: v for k, v in r.items() if k not in ("id", "position_index", "last_updated")}
            clean_rules.append(clean)
    if not found:
        return {"success": False, "errors": [{"message": f"Rule {rule_id} not found"}]}
    logger.info(f"[CF] Update managed rule: id={rule_id}")
    return _update_managed_rules(api_token, zone_id, clean_rules)


def delete_managed_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    """Delete a managed rule by removing from rules list."""
    current = list_managed_rules(api_token, zone_id)
    existing_rules = current.get("result", []) if current.get("success") else []
    clean_rules = []
    for r in existing_rules:
        if r.get("id") != rule_id:
            clean = {k: v for k, v in r.items() if k not in ("id", "position_index", "last_updated")}
            clean_rules.append(clean)
    logger.info(f"[CF] Delete managed rule: id={rule_id}")
    return _update_managed_rules(api_token, zone_id, clean_rules)


async def async_list_managed_rules(api_token: str, zone_id: str) -> dict:
    return await asyncio.to_thread(list_managed_rules, api_token, zone_id)


async def async_create_managed_rule(api_token: str, zone_id: str, data: dict) -> dict:
    return await asyncio.to_thread(create_managed_rule, api_token, zone_id, data)


async def async_update_managed_rule(api_token: str, zone_id: str, rule_id: str, data: dict) -> dict:
    return await asyncio.to_thread(update_managed_rule, api_token, zone_id, rule_id, data)


async def async_delete_managed_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    return await asyncio.to_thread(delete_managed_rule, api_token, zone_id, rule_id)
