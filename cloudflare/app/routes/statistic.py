import logging
from datetime import datetime, timezone
from fastapi import APIRouter, HTTPException

from app.services.db import query_all
from app.services.mongodb import get_db, get_domain_db

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE_COLLECTION = "statistic"
CHART_COLLECTION = "statistic_chart"


@router.post("/sync")
async def sync_statistic(body: dict):
    """POST /statistic/sync - Sync zone basic stats from CF GraphQL to MongoDB."""
    date = body.get("date") or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    group_id = body.get("groupId") or ""
    logger.info(f"[Statistic] Sync request: date={date}, groupId={group_id or 'all'}")

    import httpx

    db = await get_domain_db()
    accounts = await query_all("SELECT id, api_key, cf_account_id FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")

    zone_id_set = await _get_zone_ids(group_id)
    if zone_id_set is not None and not zone_id_set:
        return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    all_zones = await _get_zones(zone_id_set)
    logger.info(f"[Statistic] Syncing {len(all_zones)} zones for date={date}")

    account_map = {str(a["id"]): {"api_key": a["api_key"], "cf_account_id": a.get("cf_account_id", "")} for a in accounts}

    query = """
    query($zoneTag: String!, $date: String!) {
      viewer {
        zones(filter: { zoneTag: $zoneTag }) {
          httpRequests1dGroups(filter: { date: $date }, limit: 1) {
            sum { requests cachedRequests bytes threats pageViews }
            uniq { uniques }
          }
        }
      }
    }
    """

    synced = 0
    results = []

    async with httpx.AsyncClient(timeout=30) as client:
        for zone in all_zones:
            zone_id = zone["zone_id"]
            zone_name = zone["name"]
            acc = account_map.get(str(zone.get("account_id", "")))
            if not acc:
                continue
            try:
                resp = await client.post(
                    "https://api.cloudflare.com/client/v4/graphql",
                    headers={"Authorization": f"Bearer {acc['api_key']}", "Content-Type": "application/json"},
                    json={"query": query, "variables": {"zoneTag": zone_id, "date": date}},
                )
                if resp.status_code != 200:
                    continue
                data = resp.json()
                if data.get("errors"):
                    logger.warning(f"[Statistic] {zone_name}: {data['errors'][:1]}")
                    continue

                zones_data = ((data.get("data") or {}).get("viewer") or {}).get("zones") or []
                if not zones_data:
                    continue
                http_data = zones_data[0].get("httpRequests1dGroups") or []
                if not http_data:
                    continue

                s = http_data[0].get("sum") or {}
                u = http_data[0].get("uniq") or {}
                total = s.get("requests", 0)
                cached = s.get("cachedRequests", 0)
                results.append({
                    "zoneId": zone_id, "domain": zone_name, "date": date,
                    "total": total, "cached": cached, "uncached": total - cached,
                    "bandwidth": s.get("bytes", 0), "threats": s.get("threats", 0),
                    "pageViews": s.get("pageViews", 0), "uniqueVisitor": u.get("uniques", 0),
                    "syncedAt": datetime.now(timezone.utc).isoformat(),
                })
                synced += 1
            except Exception as e:
                logger.warning(f"[Statistic] {zone_name}: {e}")

    if results:
        day_col = f"{TABLE_COLLECTION}_{date.replace('-', '_')}"
        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        if date == today:
            await db[day_col].drop()
        else:
            cols = await db.list_collection_names()
            if day_col in cols and await db[day_col].count_documents({}) > 0:
                logger.info(f"[Statistic] {date} already has data, skip")
                return {"code": 200, "data": {"synced": 0}, "message": f"{date} already exists"}
        await db[day_col].insert_many(results)
        await db[day_col].create_index("domain")

    logger.info(f"[Statistic] Done: {synced} zones synced for {date}")
    return {"code": 200, "data": {"synced": synced}, "message": f"Synced {synced} zone stat for {date}"}


@router.post("/sync/month")
async def sync_statistic_month(body: dict):
    """POST /statistic/sync/month - Sync all days in a month and aggregate."""
    from calendar import monthrange

    month = body.get("month") or datetime.now(timezone.utc).strftime("%Y-%m")
    group_id = body.get("groupId") or ""
    year, mon = int(month.split("-")[0]), int(month.split("-")[1])
    _, days_in_month = monthrange(year, mon)
    logger.info(f"[Statistic] Month sync: {month} ({days_in_month} days)")

    import httpx

    db = await get_domain_db()
    accounts = await query_all("SELECT id, api_key, cf_account_id FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")

    zone_id_set = await _get_zone_ids(group_id)
    if zone_id_set is not None and not zone_id_set:
        return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    all_zones = await _get_zones(zone_id_set)
    account_map = {str(a["id"]): {"api_key": a["api_key"], "cf_account_id": a.get("cf_account_id", "")} for a in accounts}

    query = """
    query($zoneTag: String!, $date: String!) {
      viewer {
        zones(filter: { zoneTag: $zoneTag }) {
          httpRequests1dGroups(filter: { date: $date }, limit: 1) {
            sum { requests cachedRequests bytes threats pageViews }
            uniq { uniques }
          }
        }
      }
    }
    """

    total_synced = 0
    skipped = 0
    cols = await db.list_collection_names()

    async with httpx.AsyncClient(timeout=30) as client:
        for day in range(1, days_in_month + 1):
            date = f"{month}-{day:02d}"
            day_col = f"{TABLE_COLLECTION}_{date.replace('-', '_')}"

            # Skip if day already has data (except last day)
            is_last_day = (day == days_in_month)
            if not is_last_day and day_col in cols and await db[day_col].count_documents({}) > 0:
                skipped += 1
                continue
            if is_last_day and day_col in cols:
                await db[day_col].drop()

            # Fetch from CF for this day
            day_results = []
            for zone in all_zones:
                zone_id = zone["zone_id"]
                zone_name = zone["name"]
                acc = account_map.get(str(zone.get("account_id", "")))
                if not acc:
                    continue
                try:
                    resp = await client.post(
                        "https://api.cloudflare.com/client/v4/graphql",
                        headers={"Authorization": f"Bearer {acc['api_key']}", "Content-Type": "application/json"},
                        json={"query": query, "variables": {"zoneTag": zone_id, "date": date}},
                    )
                    if resp.status_code != 200:
                        continue
                    data = resp.json()
                    if data.get("errors"):
                        continue
                    zones_data = ((data.get("data") or {}).get("viewer") or {}).get("zones") or []
                    if not zones_data:
                        continue
                    http_data = zones_data[0].get("httpRequests1dGroups") or []
                    if not http_data:
                        continue
                    s = http_data[0].get("sum") or {}
                    u = http_data[0].get("uniq") or {}
                    total = s.get("requests", 0)
                    cached = s.get("cachedRequests", 0)
                    day_results.append({
                        "zoneId": zone_id, "domain": zone_name, "date": date,
                        "total": total, "cached": cached, "uncached": total - cached,
                        "bandwidth": s.get("bytes", 0), "threats": s.get("threats", 0),
                        "pageViews": s.get("pageViews", 0), "uniqueVisitor": u.get("uniques", 0),
                        "syncedAt": datetime.now(timezone.utc).isoformat(),
                    })
                    total_synced += 1
                except Exception as e:
                    logger.warning(f"[Statistic] month {zone_name} {date}: {e}")

            # Write daily collection
            if day_results:
                await db[day_col].insert_many(day_results)
                await db[day_col].create_index("domain")
                logger.info(f"[Statistic] Wrote {day_col}: {len(day_results)} docs")

    logger.info(f"[Statistic] Month done: {total_synced} synced, {skipped} skipped")
    return {"code": 200, "data": {"synced": total_synced, "skipped": skipped}, "message": f"Month {month}: {total_synced} synced, {skipped} skipped"}


@router.post("/sync/chart/month")
async def sync_statistic_chart_month(body: dict):
    """POST /statistic/sync/chart/month - Sync chart data for all days in a month."""
    from calendar import monthrange

    month = body.get("month") or datetime.now(timezone.utc).strftime("%Y-%m")
    group_id = body.get("groupId") or ""
    year, mon = int(month.split("-")[0]), int(month.split("-")[1])
    _, days_in_month = monthrange(year, mon)
    logger.info(f"[Statistic] Chart month sync: {month}")

    import httpx

    db = await get_domain_db()
    accounts = await query_all("SELECT id, api_key, cf_account_id FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")

    zone_id_set = await _get_zone_ids(group_id)
    if zone_id_set is not None and not zone_id_set:
        return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    all_zones = await _get_zones(zone_id_set)
    account_map = {str(a["id"]): {"api_key": a["api_key"], "cf_account_id": a.get("cf_account_id", "")} for a in accounts}
    country_query = """
    query($accountTag: String!, $filter: ZoneHttpRequestsAdaptiveGroupsFilter_InputObject) {
      viewer {
        accounts(filter: { accountTag: $accountTag }) {
          series: httpRequestsAdaptiveGroups(limit: 5000, filter: $filter) {
            count
            dimensions { clientCountryName }
          }
        }
      }
    }
    """

    ip_query = """
    query($accountTag: String!, $filter: ZoneHttpRequestsAdaptiveGroupsFilter_InputObject) {
      viewer {
        accounts(filter: { accountTag: $accountTag }) {
          series: httpRequestsAdaptiveGroups(limit: 5000, filter: $filter) {
            count
            dimensions { clientIP clientCountryName }
          }
        }
      }
    }
    """

    total_synced = 0
    skipped = 0
    cols = await db.list_collection_names()

    async with httpx.AsyncClient(timeout=60) as client:
        for day in range(1, days_in_month + 1):
            date = f"{month}-{day:02d}"
            day_col = f"{CHART_COLLECTION}_{date.replace('-', '_')}"

            is_last_day = (day == days_in_month)
            if not is_last_day and day_col in cols and await db[day_col].count_documents({}) > 0:
                skipped += 1
                continue
            if is_last_day and day_col in cols:
                await db[day_col].drop()

            dt_start = f"{date}T00:00:00Z"
            dt_end = f"{date}T23:59:59Z"
            day_results = []

            for zone in all_zones:
                zone_id = zone["zone_id"]
                zone_name = zone["name"]
                acc = account_map.get(str(zone.get("account_id", "")))
                if not acc or not acc["cf_account_id"]:
                    continue
                try:
                    resp = await client.post(
                        "https://api.cloudflare.com/client/v4/graphql",
                        headers={"Authorization": f"Bearer {acc['api_key']}", "Content-Type": "application/json"},
                        json={"query": country_query, "variables": {
                            "accountTag": acc["cf_account_id"],
                            "filter": {"AND": [{"datetime_geq": dt_start, "datetime_leq": dt_end}, {"zoneTag": zone_id}]}
                        }},
                    )
                    top_countries = []
                    if resp.status_code == 200:
                        data = resp.json()
                        if not data.get("errors"):
                            accounts_data = ((data.get("data") or {}).get("viewer") or {}).get("accounts") or []
                            for s in (accounts_data[0].get("series") if accounts_data else []):
                                dim = s.get("dimensions") or {}
                                country = dim.get("clientCountryName", "")
                                if country and country != "XX":
                                    top_countries.append({"country": country, "requests": s.get("count", 0)})
                            top_countries.sort(key=lambda x: x["requests"], reverse=True)

                    top_ips = []
                    try:
                        ip_resp = await client.post(
                            "https://api.cloudflare.com/client/v4/graphql",
                            headers={"Authorization": f"Bearer {acc['api_key']}", "Content-Type": "application/json"},
                            json={"query": ip_query, "variables": {
                                "accountTag": acc["cf_account_id"],
                                "filter": {"AND": [{"datetime_geq": dt_start, "datetime_leq": dt_end}, {"zoneTag": zone_id}]}
                            }},
                        )
                        if ip_resp.status_code == 200:
                            ip_data = ip_resp.json()
                            if not ip_data.get("errors"):
                                ip_accounts = ((ip_data.get("data") or {}).get("viewer") or {}).get("accounts") or []
                                for s in (ip_accounts[0].get("series") if ip_accounts else []):
                                    dim = s.get("dimensions") or {}
                                    ip = dim.get("clientIP", "")
                                    if ip:
                                        top_ips.append({"ip": ip, "country": dim.get("clientCountryName", ""), "requests": s.get("count", 0)})
                                ip_merged: dict = {}
                                for item in top_ips:
                                    k = item["ip"]
                                    if k in ip_merged:
                                        ip_merged[k]["requests"] += item["requests"]
                                    else:
                                        ip_merged[k] = {"ip": item["ip"], "country": item["country"], "requests": item["requests"]}
                                top_ips = sorted(ip_merged.values(), key=lambda x: x["requests"], reverse=True)
                    except Exception:
                        pass

                    day_results.append({
                        "zoneId": zone_id, "domain": zone_name, "date": date,
                        "topCountries": top_countries[:10],
                        "topIPs": top_ips[:50],
                        "syncedAt": datetime.now(timezone.utc).isoformat(),
                    })
                    total_synced += 1
                except Exception as e:
                    logger.warning(f"[Statistic] chart month {zone_name} {date}: {e}")

            if day_results:
                await db[day_col].insert_many(day_results)
                await db[day_col].create_index("domain")

    logger.info(f"[Statistic] Chart month done: {total_synced} synced, {skipped} skipped")
    return {"code": 200, "data": {"synced": total_synced, "skipped": skipped}, "message": f"Chart {month}: {total_synced} synced, {skipped} skipped"}


@router.post("/sync/chart")
async def sync_statistic_chart(body: dict):
    """POST /statistic/sync/chart - Sync country breakdown from CF to MongoDB."""
    date = body.get("date") or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    group_id = body.get("groupId") or ""
    logger.info(f"[Statistic] Chart sync: date={date}, groupId={group_id or 'all'}")

    import httpx

    db = await get_domain_db()
    accounts = await query_all("SELECT id, api_key, cf_account_id FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")

    zone_id_set = await _get_zone_ids(group_id)
    if zone_id_set is not None and not zone_id_set:
        return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    all_zones = await _get_zones(zone_id_set)
    logger.info(f"[Statistic] Chart: {len(all_zones)} zones for date={date}")

    account_map = {str(a["id"]): {"api_key": a["api_key"], "cf_account_id": a.get("cf_account_id", "")} for a in accounts}
    dt_start = f"{date}T00:00:00Z"
    dt_end = f"{date}T23:59:59Z"

    country_query = """
    query($accountTag: String!, $filter: ZoneHttpRequestsAdaptiveGroupsFilter_InputObject) {
      viewer {
        accounts(filter: { accountTag: $accountTag }) {
          series: httpRequestsAdaptiveGroups(limit: 5000, filter: $filter) {
            count
            dimensions { clientCountryName }
          }
        }
      }
    }
    """

    ip_query = """
    query($accountTag: String!, $filter: ZoneHttpRequestsAdaptiveGroupsFilter_InputObject) {
      viewer {
        accounts(filter: { accountTag: $accountTag }) {
          series: httpRequestsAdaptiveGroups(limit: 5000, filter: $filter) {
            count
            dimensions { clientIP clientCountryName }
          }
        }
      }
    }
    """

    results = []

    async with httpx.AsyncClient(timeout=60) as client:
        for zone in all_zones:
            zone_id = zone["zone_id"]
            zone_name = zone["name"]
            acc = account_map.get(str(zone.get("account_id", "")))
            if not acc or not acc["cf_account_id"]:
                logger.warning(f"[Statistic] chart {zone_name}: no cf_account_id for account_id={zone.get('account_id')}")
                continue
            try:
                resp = await client.post(
                    "https://api.cloudflare.com/client/v4/graphql",
                    headers={"Authorization": f"Bearer {acc['api_key']}", "Content-Type": "application/json"},
                    json={
                        "query": country_query,
                        "variables": {
                            "accountTag": acc["cf_account_id"],
                            "filter": {"AND": [{"datetime_geq": dt_start, "datetime_leq": dt_end}, {"zoneTag": zone_id}]}
                        }
                    },
                )
                if resp.status_code != 200:
                    continue
                data = resp.json()
                if data.get("errors"):
                    logger.warning(f"[Statistic] chart {zone_name}: {data['errors'][:1]}")
                    continue

                accounts_data = ((data.get("data") or {}).get("viewer") or {}).get("accounts") or []
                if not accounts_data:
                    continue

                top_countries = []
                for s in (accounts_data[0].get("series") or []):
                    dim = s.get("dimensions") or {}
                    country = dim.get("clientCountryName", "")
                    if not country or country == "XX":
                        continue
                    top_countries.append({"country": country, "requests": s.get("count", 0)})

                top_countries.sort(key=lambda x: x["requests"], reverse=True)

                # Query client IPs
                top_ips = []
                try:
                    ip_resp = await client.post(
                        "https://api.cloudflare.com/client/v4/graphql",
                        headers={"Authorization": f"Bearer {acc['api_key']}", "Content-Type": "application/json"},
                        json={
                            "query": ip_query,
                            "variables": {
                                "accountTag": acc["cf_account_id"],
                                "filter": {"AND": [{"datetime_geq": dt_start, "datetime_leq": dt_end}, {"zoneTag": zone_id}]}
                            }
                        },
                    )
                    if ip_resp.status_code == 200:
                        ip_data = ip_resp.json()
                        if not ip_data.get("errors"):
                            ip_accounts = ((ip_data.get("data") or {}).get("viewer") or {}).get("accounts") or []
                            for s in (ip_accounts[0].get("series") if ip_accounts else []):
                                dim = s.get("dimensions") or {}
                                ip = dim.get("clientIP", "")
                                if ip:
                                    top_ips.append({"ip": ip, "country": dim.get("clientCountryName", ""), "requests": s.get("count", 0)})
                            # Merge same IPs (may appear with different countries)
                            ip_merged: dict = {}
                            for item in top_ips:
                                k = item["ip"]
                                if k in ip_merged:
                                    ip_merged[k]["requests"] += item["requests"]
                                else:
                                    ip_merged[k] = {"ip": item["ip"], "country": item["country"], "requests": item["requests"]}
                            top_ips = sorted(ip_merged.values(), key=lambda x: x["requests"], reverse=True)
                except Exception as e:
                    logger.warning(f"[Statistic] IP query {zone_name}: {e}")

                results.append({
                    "zoneId": zone_id, "domain": zone_name, "date": date,
                    "topCountries": top_countries[:10],
                    "topIPs": top_ips[:50],
                    "syncedAt": datetime.now(timezone.utc).isoformat(),
                })
            except Exception as e:
                logger.warning(f"[Statistic] chart {zone_name}: {e}")

    if results:
        day_col = f"{CHART_COLLECTION}_{date.replace('-', '_')}"
        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        if date == today:
            await db[day_col].drop()
        else:
            cols = await db.list_collection_names()
            if day_col in cols and await db[day_col].count_documents({}) > 0:
                logger.info(f"[Statistic] Chart {date} already has data, skip")
                return {"code": 200, "data": {"synced": 0}, "message": f"Chart {date} already exists"}
        await db[day_col].insert_many(results)
        await db[day_col].create_index("domain")

    logger.info(f"[Statistic] Chart done: {len(results)} zones for {date}")
    return {"code": 200, "data": {"synced": len(results)}, "message": f"Synced {len(results)} chart data for {date}"}


async def _get_zone_ids(group_id: str):
    """Get zoneIds from domain.domain_meta for a group."""
    if not group_id:
        return None
    db = await get_domain_db()
    meta_cursor = db["domain_meta"].find({"groupId": group_id}, {"zoneId": 1})
    meta_list = await meta_cursor.to_list(length=5000)
    return {m["zoneId"] for m in meta_list if m.get("zoneId")}


async def _get_zones(zone_id_set):
    """Collect zones from cloudflare MongoDB."""
    db = await get_db()
    all_zones = []
    collections = await db.list_collection_names()
    for col_name in collections:
        if col_name.endswith("_zones"):
            col = db[col_name]
            q = {"zone_id": {"$in": list(zone_id_set)}} if zone_id_set else {}
            async for zone in col.find(q, {"zone_id": 1, "name": 1, "account_id": 1}):
                if zone.get("zone_id") and zone.get("name"):
                    all_zones.append(zone)
    return all_zones
