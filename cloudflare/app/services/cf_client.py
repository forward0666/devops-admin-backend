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

def list_zones(api_token: str, per_page: int = 50) -> dict:
    cf = get_client(api_token)
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


def create_dns(api_token: str, zone_id: str, data: dict) -> dict:
    cf = get_client(api_token)
    resp = cf.dns.records.create(zone_id=zone_id, **data)
    return {"success": True, "result": resp.model_dump()}


def update_dns(api_token: str, zone_id: str, record_id: str, data: dict) -> dict:
    cf = get_client(api_token)
    resp = cf.dns.records.update(dns_record_id=record_id, zone_id=zone_id, **data)
    return {"success": True, "result": resp.model_dump()}


def delete_dns(api_token: str, zone_id: str, record_id: str) -> dict:
    cf = get_client(api_token)
    cf.dns.records.delete(dns_record_id=record_id, zone_id=zone_id)
    return {"success": True, "result": None}


def list_firewall_rules(api_token: str, zone_id: str) -> dict:
    cf = get_client(api_token)
    resp = cf.firewall.rules.list(zone_id=zone_id)
    result = [r.model_dump() for r in _auto_paginate(resp)]
    return {"success": True, "result": result, "result_info": {}}


def create_firewall_rule(api_token: str, zone_id: str, data: dict) -> dict:
    cf = get_client(api_token)
    resp = cf.firewall.rules.create(zone_id=zone_id, **data)
    return {"success": True, "result": resp.model_dump()}


def get_firewall_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    cf = get_client(api_token)
    resp = cf.firewall.rules.get(rule_id=rule_id, zone_id=zone_id)
    return {"success": True, "result": resp.model_dump()}


def update_firewall_rule(api_token: str, zone_id: str, rule_id: str, data: dict) -> dict:
    cf = get_client(api_token)
    resp = cf.firewall.rules.update(rule_id=rule_id, zone_id=zone_id, **data)
    return {"success": True, "result": resp.model_dump()}


def delete_firewall_rule(api_token: str, zone_id: str, rule_id: str) -> dict:
    cf = get_client(api_token)
    cf.firewall.rules.delete(rule_id=rule_id, zone_id=zone_id)
    return {"success": True, "result": None}


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


def list_cache_rules(api_token: str, zone_id: str) -> dict:
    """Fetch cache rules via Cloudflare REST API."""
    cf = get_client(api_token)
    resp = cf.rulesets.list(zone_id=zone_id)
    cache_ruleset = None
    for r in resp:
        r_dict = r.model_dump()
        if r_dict.get("phase") == "http_request_cache_settings":
            cache_ruleset = r_dict
            break
    if not cache_ruleset:
        return {"success": True, "result": []}

    ruleset_id = cache_ruleset["id"]
    detail = cf.rulesets.get(ruleset_id=ruleset_id, zone_id=zone_id)
    result = [rule.model_dump() for rule in (detail.rules or [])]
    return {"success": True, "result": result}


# ── Async wrappers (non-blocking, runs sync code in thread pool) ──

async def async_list_zones(api_token: str, per_page: int = 50) -> dict:
    return await asyncio.to_thread(list_zones, api_token, per_page)

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

async def async_get_client(api_token: str) -> Cloudflare:
    return await asyncio.to_thread(get_client, api_token)
