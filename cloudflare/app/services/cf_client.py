import logging
from cloudflare import Cloudflare

logger = logging.getLogger(__name__)


def get_client(api_token: str) -> Cloudflare:
    return Cloudflare(api_token=api_token)


def list_zones(api_token: str, per_page: int = 50) -> dict:
    cf = get_client(api_token)
    resp = cf.zones.list(per_page=per_page)
    return {"success": True, "result": [z.model_dump() for z in resp], "result_info": {}}


def get_zone(api_token: str, zone_id: str) -> dict:
    cf = get_client(api_token)
    resp = cf.zones.get(zone_id=zone_id)
    return {"success": True, "result": resp.model_dump()}


def list_dns(api_token: str, zone_id: str, per_page: int = 100) -> dict:
    cf = get_client(api_token)
    resp = cf.dns.records.list(zone_id=zone_id, per_page=per_page)
    return {"success": True, "result": [r.model_dump() for r in resp], "result_info": {}}


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
    return {"success": True, "result": [r.model_dump() for r in resp], "result_info": {}}


def create_firewall_rule(api_token: str, zone_id: str, data: dict) -> dict:
    cf = get_client(api_token)
    resp = cf.firewall.rules.create(zone_id=zone_id, **data)
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
    return {"success": True, "result": resp.model_dump()}


def purge_by_urls(api_token: str, zone_id: str, files: list) -> dict:
    cf = get_client(api_token)
    resp = cf.cache.purge(zone_id=zone_id, files=files)
    return {"success": True, "result": resp.model_dump()}


def purge_by_tags(api_token: str, zone_id: str, tags: list) -> dict:
    cf = get_client(api_token)
    resp = cf.cache.purge(zone_id=zone_id, tags=tags)
    return {"success": True, "result": resp.model_dump()}


def purge_by_hosts(api_token: str, zone_id: str, hosts: list) -> dict:
    cf = get_client(api_token)
    resp = cf.cache.purge(zone_id=zone_id, hosts=hosts)
    return {"success": True, "result": resp.model_dump()}
