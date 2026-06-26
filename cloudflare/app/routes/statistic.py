import logging
import asyncio
from datetime import datetime, timezone
from fastapi import APIRouter, HTTPException

from app.services.db import query_all
from app.services.mongodb import get_db, get_domain_db
from app.services.redis import get_redis

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE_COLLECTION = "statistic"
CHART_COLLECTION = "statistic_chart"


async def _cf_post(client, url, headers=None, json=None, max_retries=3):
    """Post to CF GraphQL with retry on rate limit."""
    for attempt in range(max_retries):
        resp = await client.post(url, headers=headers, json=json)
        if resp.status_code == 200:
            data = resp.json()
            errors = data.get("errors") or []
            if any("budget" in e.get("extensions", {}).get("code", "") or "Rate limiter" in e.get("message", "") for e in errors):
                wait = (attempt + 1) * 30
                logger.warning(f"[Statistic] Rate limited, wait {wait}s (attempt {attempt + 1}/{max_retries})")
                await asyncio.sleep(wait)
                continue
            return resp
        if resp.status_code == 429:
            wait = (attempt + 1) * 30
            logger.warning(f"[Statistic] HTTP 429, wait {wait}s (attempt {attempt + 1}/{max_retries})")
            await asyncio.sleep(wait)
            continue
        return resp
    return resp


@router.post("/sync")
async def sync_statistic(body: dict):
    """POST /statistic/sync - Sync zone basic stats from CF GraphQL to MongoDB.
    Uses account-level httpRequestsAdaptiveGroups grouped by zoneTag → 1 request per account."""
    date = body.get("date") or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    group_id = body.get("groupId") or ""
    logger.info(f"[Statistic] Step 1: Day sync start: date={date}")

    import httpx

    db = await get_domain_db()
    logger.info(f"[Statistic] Step 2: MongoDB connected")

    accounts = await query_all("SELECT id, api_key, cf_account_id FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")
    logger.info(f"[Statistic] Step 3: Found {len(accounts)} accounts")

    zone_id_set = await _get_zone_ids(group_id)
    if zone_id_set is not None and not zone_id_set:
        return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    all_zones = await _get_zones(zone_id_set)
    account_map = {str(a["id"]): {"api_key": a["api_key"], "cf_account_id": a.get("cf_account_id", "")} for a in accounts}
    logger.info(f"[Statistic] Step 4: Found {len(all_zones)} zones")

    # Check BEFORE fetching
    day_col = f"{TABLE_COLLECTION}_{date.replace('-', '_')}"
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    cols = await db.list_collection_names()
    if date != today and day_col in cols:
        existing_zone_ids = set()
        async for doc in db[day_col].find({}, {"zoneId": 1}):
            existing_zone_ids.add(doc.get("zoneId"))
        required_zone_ids = {z["zone_id"] for z in all_zones}
        missing = required_zone_ids - existing_zone_ids
        logger.info(f"[Statistic] Step 5: {day_col} has {len(existing_zone_ids)} zones, missing {len(missing)}")
        if not missing:
            logger.info(f"[Statistic] Step 5: all zones present -> SKIP")
            return {"code": 200, "data": {"synced": 0}, "message": f"{date} already exists"}
    if date == today and day_col in cols:
        await db[day_col].drop()
        logger.info(f"[Statistic] Step 5: Today → DROP {day_col}")
    # Filter out zones that already have data
    if day_col in cols:
        existing_zones = set()
        async for doc in db[day_col].find({}, {"zoneId": 1}):
            existing_zones.add(doc.get("zoneId"))
        before = len(all_zones)
        all_zones = [z for z in all_zones if z["zone_id"] not in existing_zones]
        logger.info(f"[Statistic] Step 5: {before} total, {len(all_zones)} missing, {len(existing_zones)} exist")

    # Group zones by account for batch querying
    acc_zones = {}  # account_db_id -> list of zones
    for z in all_zones:
        aid = str(z.get("account_id", ""))
        acc_zones.setdefault(aid, []).append(z)

    table_query = """
    query($accountTag: String!, $dateStart: String!, $dateEnd: String!) {
      viewer {
        accounts(filter: { accountTag: $accountTag }) {
          httpRequestsAdaptiveGroups(limit: 10000, filter: { datetime_geq: $dateStart, datetime_leq: $dateEnd }) {
            sum { requests cachedRequests bytes threats pageViews }
            uniq { uniques }
            dimensions { zoneTag }
          }
        }
      }
    }
    """
    dt_start = f"{date}T00:00:00Z"
    dt_end = f"{date}T23:59:59Z"

    logger.info(f"[Statistic] Step 5: Fetching {len(all_zones)} zones via {len(acc_zones)} account-level queries...")

    cf_data = {}  # zone_id -> {total, cached, ...}
    async with httpx.AsyncClient(timeout=60) as client:
        for aid, zones in acc_zones.items():
            acc = account_map.get(aid)
            if not acc or not acc.get("cf_account_id"):
                continue
            try:
                resp = await _cf_post(
                    client, "https://api.cloudflare.com/client/v4/graphql",
                    headers={"Authorization": f"Bearer {acc['api_key']}", "Content-Type": "application/json"},
                    json={"query": table_query, "variables": {
                        "accountTag": acc["cf_account_id"],
                        "dateStart": dt_start, "dateEnd": dt_end,
                    }},
                )
                if resp.status_code != 200:
                    logger.warning(f"[Statistic] Account {aid}: HTTP {resp.status_code}")
                    continue
                data = resp.json()
                if data.get("errors"):
                    logger.warning(f"[Statistic] Account {aid}: {data['errors'][0].get('message', '')}")
                    continue
                groups = (((data.get("data") or {}).get("viewer") or {}).get("accounts") or [{}])[0].get("httpRequestsAdaptiveGroups") or []
                for g in groups:
                    zid = (g.get("dimensions") or {}).get("zoneTag", "")
                    if not zid:
                        continue
                    s = g.get("sum") or {}
                    u = g.get("uniq") or {}
                    total = s.get("requests", 0)
                    cached = s.get("cachedRequests", 0)
                    cf_data[zid] = {
                        "total": total, "cached": cached, "uncached": total - cached,
                        "bandwidth": s.get("bytes", 0), "threats": s.get("threats", 0),
                        "pageViews": s.get("pageViews", 0), "uniqueVisitor": u.get("uniques", 0),
                    }
                logger.info(f"[Statistic] Account {aid}: {len(groups)} zone rows from CF ({len(zones)} zones expected)")
            except Exception as e:
                logger.warning(f"[Statistic] Account {aid}: {e}")

    # Build results: merge zone metadata with CF data
    results = []
    synced = 0
    for zone in all_zones:
        zid = zone["zone_id"]
        d = cf_data.get(zid)
        if d:
            results.append({"zoneId": zid, "domain": zone["name"], "date": date, **d,
                            "syncedAt": datetime.now(timezone.utc).isoformat()})
            synced += 1
        else:
            results.append({"zoneId": zid, "domain": zone["name"], "date": date,
                            "total": 0, "cached": 0, "uncached": 0, "bandwidth": 0, "threats": 0,
                            "pageViews": 0, "uniqueVisitor": 0,
                            "syncedAt": datetime.now(timezone.utc).isoformat()})
    logger.info(f"[Statistic] Step 5b: {synced} with data, {len(results)} total (incl zeros)")

    if results:
        # Clear Redis cache for today
        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        if date == today:
            month_prefix = date[:7]
            r = await get_redis()
            keys = []
            async for k in r.scan_iter(f"stat:*{date}*"):
                keys.append(k)
            async for k in r.scan_iter(f"stat:*{month_prefix}*"):
                keys.append(k)
            if keys:
                await r.delete(*keys)
                logger.info(f"[Statistic] Step 6: Cleared {len(keys)} cache keys")
        await db[day_col].insert_many(results)
        await db[day_col].create_index("domain")
        await db[day_col].create_index([("zoneId", 1)], unique=True)
        logger.info(f"[Statistic] Step 6: Wrote {len(results)} docs -> {day_col}")
    else:
        logger.info(f"[Statistic] Step 6: No data from CF")

    logger.info(f"[Statistic] Step 7: Done: {synced} zones synced for {date}")
    return {"code": 200, "data": {"synced": synced}, "message": f"Synced {synced} zone stat for {date}"}


@router.post("/sync/chart")
async def sync_statistic_chart(body: dict):
    """POST /statistic/sync/chart - Sync country+IP breakdown from CF to MongoDB.
    Countries: account-level query grouped by zoneTag+clientCountryName (1 request per account).
    IPs: per-zone query (volume too high for account-level batching)."""
    date = body.get("date") or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    group_id = body.get("groupId") or ""
    logger.info(f"[Statistic] Step 1: Chart day sync start: date={date}")

    import httpx

    db = await get_domain_db()
    logger.info(f"[Statistic] Step 2: MongoDB connected")

    accounts = await query_all("SELECT id, api_key, cf_account_id FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")
    logger.info(f"[Statistic] Step 3: Found {len(accounts)} accounts")

    zone_id_set = await _get_zone_ids(group_id)
    logger.info(f"[Statistic] Step 4a: groupId={group_id or 'ALL'}, zone_id_set={len(zone_id_set) if zone_id_set is not None else 'ALL'}")
    if zone_id_set is not None and not zone_id_set:
        return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    all_zones = await _get_zones(zone_id_set)
    account_map = {str(a["id"]): {"api_key": a["api_key"], "cf_account_id": a.get("cf_account_id", "")} for a in accounts}
    logger.info(f"[Statistic] Step 4b: Found {len(all_zones)} zones")

    dt_start = f"{date}T00:00:00Z"
    dt_end = f"{date}T23:59:59Z"

    # Check if data already exists
    day_col = f"{CHART_COLLECTION}_{date.replace('-', '_')}"
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    cols = await db.list_collection_names()
    logger.info(f"[Statistic] Step 5: Chart {date}, today={today}, col={day_col}, exists={day_col in cols}")
    if date != today and day_col in cols:
        existing_count = await db[day_col].count_documents({})
        if existing_count > 0:
            logger.info(f"[Statistic] Step 5: Chart {date} has {existing_count} docs -> SKIP")
            return {"code": 200, "data": {"synced": 0}, "message": f"Chart {date} already exists"}
    if date == today and day_col in cols:
        await db[day_col].drop()
        logger.info(f"[Statistic] Step 5: Today -> DROP {day_col}")

    # Group zones by account
    acc_zones = {}
    for z in all_zones:
        aid = str(z.get("account_id", ""))
        acc_zones.setdefault(aid, []).append(z)

    # --- Phase 1: Fetch countries via account-level batch query (1 request per account) ---
    country_query = """
    query($accountTag: String!, $dateStart: String!, $dateEnd: String!) {
      viewer {
        accounts(filter: { accountTag: $accountTag }) {
          httpRequestsAdaptiveGroups(limit: 10000, filter: { datetime_geq: $dateStart, datetime_leq: $dateEnd }) {
            count
            dimensions { zoneTag clientCountryName }
          }
        }
      }
    }
    """

    zone_countries = {}  # zone_id -> [{country, requests}]
    async with httpx.AsyncClient(timeout=60) as client:
        for aid, zones in acc_zones.items():
            acc = account_map.get(aid)
            if not acc or not acc.get("cf_account_id"):
                continue
            try:
                resp = await _cf_post(
                    client, "https://api.cloudflare.com/client/v4/graphql",
                    headers={"Authorization": f"Bearer {acc['api_key']}", "Content-Type": "application/json"},
                    json={"query": country_query, "variables": {
                        "accountTag": acc["cf_account_id"],
                        "dateStart": dt_start, "dateEnd": dt_end,
                    }},
                )
                if resp.status_code != 200:
                    logger.warning(f"[Statistic] Chart countries account {aid}: HTTP {resp.status_code}")
                    continue
                data = resp.json()
                if data.get("errors"):
                    logger.warning(f"[Statistic] Chart countries account {aid}: {data['errors'][0].get('message', '')}")
                    continue
                groups = (((data.get("data") or {}).get("viewer") or {}).get("accounts") or [{}])[0].get("httpRequestsAdaptiveGroups") or []
                for g in groups:
                    dim = g.get("dimensions") or {}
                    zid = dim.get("zoneTag", "")
                    country = dim.get("clientCountryName", "")
                    if zid and country and country != "XX":
                        zone_countries.setdefault(zid, []).append({"country": country, "requests": g.get("count", 0)})
                logger.info(f"[Statistic] Chart countries account {aid}: {len(groups)} zone\u00d7country rows")
            except Exception as e:
                logger.warning(f"[Statistic] Chart countries account {aid}: {e}")

        # Sort and cap per-zone country lists
        for zid in zone_countries:
            zone_countries[zid].sort(key=lambda x: x["requests"], reverse=True)
            zone_countries[zid] = zone_countries[zid][:10]

        # --- Phase 2: Fetch IPs per-zone (volume too high for batch) ---
        ip_query = """
        query($accountTag: String!, $ipFilter: ZoneHttpRequestsAdaptiveGroupsFilter_InputObject) {
          viewer {
            accounts(filter: { accountTag: $accountTag }) {
              ips: httpRequestsAdaptiveGroups(limit: 5000, filter: $ipFilter) {
                count
                dimensions { clientIP clientCountryName }
              }
            }
          }
        }
        """

        results = []
        processed = 0
        sem = asyncio.Semaphore(10)
        lock = asyncio.Lock()

        async def _fetch_ips(client, zone):
            nonlocal processed
            zone_id = zone["zone_id"]
            zone_name = zone["name"]
            acc = account_map.get(str(zone.get("account_id", "")))
            if not acc or not acc.get("cf_account_id"):
                return
            zone_filter = {"AND": [{"datetime_geq": dt_start, "datetime_leq": dt_end}, {"zoneTag": zone_id}]}
            async with sem:
                top_ips = []
                try:
                    resp = await _cf_post(
                        client, "https://api.cloudflare.com/client/v4/graphql",
                        headers={"Authorization": f"Bearer {acc['api_key']}", "Content-Type": "application/json"},
                        json={"query": ip_query, "variables": {
                            "accountTag": acc["cf_account_id"],
                            "ipFilter": zone_filter,
                        }},
                    )
                    if resp.status_code == 200:
                        data = resp.json()
                        if not data.get("errors"):
                            accounts_data = ((data.get("data") or {}).get("viewer") or {}).get("accounts") or []
                            if accounts_data:
                                ip_merged = {}
                                for s in (accounts_data[0].get("ips") or []):
                                    dim = s.get("dimensions") or {}
                                    ip = dim.get("clientIP", "")
                                    if ip:
                                        if ip in ip_merged:
                                            ip_merged[ip]["requests"] += s.get("count", 0)
                                        else:
                                            ip_merged[ip] = {"ip": ip, "country": dim.get("clientCountryName", ""), "requests": s.get("count", 0)}
                                top_ips = sorted(ip_merged.values(), key=lambda x: x["requests"], reverse=True)[:50]
                except Exception:
                    pass
                async with lock:
                    results.append({
                        "zoneId": zone_id, "domain": zone_name, "date": date,
                        "topCountries": zone_countries.get(zone_id, []),
                        "topIPs": top_ips,
                        "syncedAt": datetime.now(timezone.utc).isoformat(),
                    })
                    processed += 1
                    if processed % 100 == 0:
                        logger.info(f"[Statistic]   IP progress: {processed}/{len(all_zones)} zones")

        logger.info(f"[Statistic] Step 5b: Fetching IPs for {len(all_zones)} zones with 10 concurrent workers...")
        tasks = [_fetch_ips(client, zone) for zone in all_zones]
        await asyncio.gather(*tasks)

    # Fill zones with no data
    fetched_zone_ids = {r["zoneId"] for r in results}
    for zone in all_zones:
        if zone["zone_id"] not in fetched_zone_ids:
            results.append({
                "zoneId": zone["zone_id"], "domain": zone["name"], "date": date,
                "topCountries": zone_countries.get(zone["zone_id"], []),
                "topIPs": [],
                "syncedAt": datetime.now(timezone.utc).isoformat(),
            })
    logger.info(f"[Statistic] Step 5c: {processed} with IP data, {len(results)} total")

    if results:
        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        if date == today:
            month_prefix = date[:7]
            r = await get_redis()
            keys = []
            async for k in r.scan_iter(f"stat:*{date}*"):
                keys.append(k)
            async for k in r.scan_iter(f"stat:*{month_prefix}*"):
                keys.append(k)
            if keys:
                await r.delete(*keys)
                logger.info(f"[Statistic] Step 6: Cleared {len(keys)} cache keys")
        await db[day_col].insert_many(results)
        await db[day_col].create_index("domain")
        await db[day_col].create_index([("zoneId", 1)], unique=True)
        logger.info(f"[Statistic] Step 6: Wrote {len(results)} docs -> {day_col}")
    else:
        logger.info(f"[Statistic] Step 6: No data from CF")

    logger.info(f"[Statistic] Step 7: Chart done: {len(results)} zones for {date}")
    return {"code": 200, "data": {"synced": len(results)}, "message": f"Synced {len(results)} chart data for {date}"}


async def _get_zone_ids(group_id: str):
    """Get zoneIds from domain.domain_meta for a group."""
    if not group_id or group_id == "all":
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
    zone_cols = [c for c in collections if c.endswith("_zones")]
    logger.info(f"[Statistic] _get_zones: {len(zone_cols)} zone collections, filter={'ALL' if zone_id_set is None else f'{len(zone_id_set)} ids'}")
    for col_name in zone_cols:
        col = db[col_name]
        q = {"zone_id": {"$in": list(zone_id_set)}} if zone_id_set else {}
        async for zone in col.find(q, {"zone_id": 1, "name": 1, "account_id": 1}):
            if zone.get("zone_id") and zone.get("name"):
                all_zones.append(zone)
    return all_zones
