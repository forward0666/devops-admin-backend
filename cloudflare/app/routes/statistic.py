import logging
import asyncio
from datetime import datetime, timezone, timedelta
from fastapi import APIRouter, HTTPException

from app.services.db import query_all
from app.services.mongodb import get_db, get_domain_db
from app.services.redis import get_redis

logger = logging.getLogger(__name__)
router = APIRouter()


async def _clear_stat_cache(dates: list):
    """Clear Redis cache for given dates (stat:* and stat_chart:* keys). Best effort."""
    try:
        r = await get_redis()
        keys = []
        for d in dates:
            async for k in r.scan_iter(f"stat:*{d}*"):
                keys.append(k)
            async for k in r.scan_iter(f"stat_chart:*{d}*"):
                keys.append(k)
        if keys:
            await r.delete(*keys)
            logger.info(f"[Statistic] Cleared {len(keys)} cache keys for {dates}")
    except Exception:
        pass

TABLE_COLLECTION = "statistic"
CHART_COLLECTION = "statistic_chart"


async def _cf_post(client, url, headers=None, json=None, max_retries=10):
    """Post to CF GraphQL with retry on rate limit / 429 / 5xx."""
    for attempt in range(max_retries):
        try:
            resp = await client.post(url, headers=headers, json=json)
        except Exception as e:
            wait = 30
            logger.warning(f"[Statistic] Network error: {e}, retry in {wait}s (attempt {attempt + 1}/{max_retries})")
            await asyncio.sleep(wait)
            continue
        if resp.status_code == 200:
            data = resp.json()
            errors = data.get("errors") or []
            rate_limited = any(
                "budget" in e.get("extensions", {}).get("code", "") or
                "Rate limiter" in e.get("message", "") or
                "rate" in e.get("message", "").lower()
                for e in errors
            )
            if rate_limited:
                logger.warning(f"[Statistic] Rate limited (GraphQL), wait 30s (attempt {attempt + 1}/{max_retries})")
                await asyncio.sleep(30)
                continue
            return resp
        if resp.status_code == 429 or resp.status_code >= 500:
            logger.warning(f"[Statistic] HTTP {resp.status_code}, wait 30s (attempt {attempt + 1}/{max_retries})")
            await asyncio.sleep(30)
            continue
        return resp
    logger.error(f"[Statistic] Max retries ({max_retries}) exhausted for {url}")
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

    # If viewing today → sync actual today + yesterday
    # If viewing past date → sync date + date-1
    actual_today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    if date == actual_today:
        # Today: DROP today + yesterday, full sync
        dates_to_sync = [actual_today, (datetime.now(timezone.utc) - timedelta(days=1)).strftime("%Y-%m-%d")]
        cols = await db.list_collection_names()
        for d in dates_to_sync:
            col_name = f"{TABLE_COLLECTION}_{d.replace('-', '_')}"
            if col_name in cols:
                await db[col_name].drop()
                logger.info(f"[Statistic] DROP {col_name} for re-sync")
        is_today_mode = True
    else:
        # Past date: check existing, only fetch missing
        dates_to_sync = [date]
        is_today_mode = False

    # Group zones by account for parallel processing
    acc_zones = {}  # account_db_id -> list of zones
    for z in all_zones:
        aid = str(z.get("account_id", ""))
        acc_zones.setdefault(aid, []).append(z)

    table_query = """
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
    all_zone_ids = {z["zone_id"] for z in all_zones}

    for sync_date in dates_to_sync:
        day_col = f"{TABLE_COLLECTION}_{sync_date.replace('-', '_')}"

        if is_today_mode:
            # Today mode: fetch all zones
            zones_to_fetch = all_zones
            fetch_acc_zones = acc_zones
        else:
            # Past mode: check existing, only fetch missing
            existing_ids = set()
            cols = await db.list_collection_names()
            if day_col in cols:
                async for doc in db[day_col].find({}, {"zoneId": 1}):
                    existing_ids.add(doc.get("zoneId"))
            missing_ids = all_zone_ids - existing_ids
            if not missing_ids:
                logger.info(f"[Statistic] ({sync_date}): all {len(all_zone_ids)} zones exist, skip")
                continue
            logger.info(f"[Statistic] ({sync_date}): {len(existing_ids)} exist, {len(missing_ids)} missing")
            zones_to_fetch = [z for z in all_zones if z["zone_id"] in missing_ids]
            fetch_acc_zones = {}
            for z in zones_to_fetch:
                aid = str(z.get("account_id", ""))
                fetch_acc_zones.setdefault(aid, []).append(z)

        cf_data = {}  # zone_id -> {total, cached, ...}
        lock = asyncio.Lock()

        async def _fetch_account(acc_id, zones, api_key):
            sem = asyncio.Semaphore(10)
            acc_ok = 0

            async def _fetch_one(client, zone):
                nonlocal acc_ok
                zone_id = zone["zone_id"]
                async with sem:
                    try:
                        resp = await _cf_post(
                            client, "https://api.cloudflare.com/client/v4/graphql",
                            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                            json={"query": table_query, "variables": {"zoneTag": zone_id, "date": sync_date}},
                        )
                        if resp.status_code != 200:
                            return
                        data = resp.json()
                        if data.get("errors"):
                            return
                        zones_data = ((data.get("data") or {}).get("viewer") or {}).get("zones") or []
                        if not zones_data:
                            return
                        http_data = zones_data[0].get("httpRequests1dGroups") or []
                        if not http_data:
                            return
                        s = http_data[0].get("sum") or {}
                        u = http_data[0].get("uniq") or {}
                        total = s.get("requests", 0)
                        cached = s.get("cachedRequests", 0)
                        async with lock:
                            cf_data[zone_id] = {
                                "total": total, "cached": cached, "uncached": total - cached,
                                "bandwidth": s.get("bytes", 0), "threats": s.get("threats", 0),
                                "pageViews": s.get("pageViews", 0), "uniqueVisitor": u.get("uniques", 0),
                            }
                            acc_ok += 1
                    except Exception:
                        pass

            async with httpx.AsyncClient(timeout=30) as client:
                tasks = [_fetch_one(client, zone) for zone in zones]
                await asyncio.gather(*tasks)
            logger.info(f"[Statistic] Table Account {acc_id} ({sync_date}): {acc_ok}/{len(zones)} zones")

        # Run accounts in parallel (only missing zones)
        acc_tasks = [_fetch_account(aid, zones, account_map.get(aid, {}).get("api_key", "")) for aid, zones in fetch_acc_zones.items()]
        await asyncio.gather(*acc_tasks)
        logger.info(f"[Statistic] Step 5 ({sync_date}): {len(cf_data)} zones fetched from CF")

        # Build results for missing zones only (CF returns 0 also written)
        results = []
        for zone in zones_to_fetch:
            zid = zone["zone_id"]
            d = cf_data.get(zid)
            if d:
                results.append({"zoneId": zid, "domain": zone["name"], "date": sync_date, **d,
                                "syncedAt": datetime.now(timezone.utc).isoformat()})
            else:
                results.append({"zoneId": zid, "domain": zone["name"], "date": sync_date,
                                "total": 0, "cached": 0, "uncached": 0, "bandwidth": 0, "threats": 0,
                                "pageViews": 0, "uniqueVisitor": 0,
                                "syncedAt": datetime.now(timezone.utc).isoformat()})
        logger.info(f"[Statistic] Step 5b ({sync_date}): {len(results)} missing zones")

        if results:
            await db[day_col].insert_many(results)
            await db[day_col].create_index("domain")
            await db[day_col].create_index([("zoneId", 1)], unique=True)
            logger.info(f"[Statistic] Step 6 ({sync_date}): Wrote {len(results)} docs -> {day_col}")
            total_synced += len(results)
        else:
            logger.info(f"[Statistic] Step 6 ({sync_date}): No data from CF")

    logger.info(f"[Statistic] Step 7: Done: {total_synced} zones synced for {'+'.join(dates_to_sync)}")
    await _clear_stat_cache(dates_to_sync)
    return {"code": 200, "data": {"synced": total_synced}, "message": f"Synced {'+'.join(dates_to_sync)}: {total_synced} zones"}


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

    # Loop dates below

    # If viewing today → sync actual today + yesterday
    # If viewing past date → sync date + date-1
    actual_today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    if date == actual_today:
        # Today: DROP today + yesterday, full sync
        dates_to_sync = [actual_today, (datetime.now(timezone.utc) - timedelta(days=1)).strftime("%Y-%m-%d")]
        cols = await db.list_collection_names()
        for d in dates_to_sync:
            col_name = f"{CHART_COLLECTION}_{d.replace('-', '_')}"
            if col_name in cols:
                await db[col_name].drop()
                logger.info(f"[Statistic] Chart DROP {col_name} for re-sync")
        is_today_mode = True
    else:
        # Past date: check existing, only fetch missing
        dates_to_sync = [date]
        is_today_mode = False

    all_zone_ids = {z["zone_id"] for z in all_zones}

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

    total_synced = 0
    for sync_date in dates_to_sync:
        day_col = f"{CHART_COLLECTION}_{sync_date.replace('-', '_')}"

        if is_today_mode:
            # Today mode: fetch all zones
            zones_to_fetch = all_zones
            fetch_acc_zones = acc_zones
        else:
            # Past mode: check existing, only fetch missing
            existing_ids = set()
            cols = await db.list_collection_names()
            if day_col in cols:
                async for doc in db[day_col].find({}, {"zoneId": 1}):
                    existing_ids.add(doc.get("zoneId"))
            missing_ids = all_zone_ids - existing_ids
            if not missing_ids:
                logger.info(f"[Statistic] Chart ({sync_date}): all {len(all_zone_ids)} zones exist, skip")
                continue
            logger.info(f"[Statistic] Chart ({sync_date}): {len(existing_ids)} exist, {len(missing_ids)} missing")
            zones_to_fetch = [z for z in all_zones if z["zone_id"] in missing_ids]
            fetch_acc_zones = {}
            for z in zones_to_fetch:
                aid = str(z.get("account_id", ""))
                fetch_acc_zones.setdefault(aid, []).append(z)

        dt_start = f"{sync_date}T00:00:00Z"
        dt_end = f"{sync_date}T23:59:59Z"

        # --- Phase 1: Countries (1 request per account, filter to missing zones) ---
        zone_countries = {}  # zone_id -> [{country, requests}]
        async with httpx.AsyncClient(timeout=60) as client:
            for aid in fetch_acc_zones:
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
                        logger.warning(f"[Statistic] Chart countries account {aid} ({sync_date}): HTTP {resp.status_code}")
                        continue
                    data = resp.json()
                    if data.get("errors"):
                        logger.warning(f"[Statistic] Chart countries account {aid} ({sync_date}): {data['errors'][0].get('message', '')}")
                        continue
                    groups = (((data.get("data") or {}).get("viewer") or {}).get("accounts") or [{}])[0].get("httpRequestsAdaptiveGroups") or []
                    for g in groups:
                        dim = g.get("dimensions") or {}
                        zid = dim.get("zoneTag", "")
                        country = dim.get("clientCountryName", "")
                        if zid and country and country != "XX":
                            zone_countries.setdefault(zid, []).append({"country": country, "requests": g.get("count", 0)})
                    logger.info(f"[Statistic] Chart countries account {aid} ({sync_date}): {len(groups)} rows")
                except Exception as e:
                    logger.warning(f"[Statistic] Chart countries account {aid} ({sync_date}): {e}")

            for zid in zone_countries:
                zone_countries[zid].sort(key=lambda x: x["requests"], reverse=True)
                zone_countries[zid] = zone_countries[zid][:10]

        # --- Phase 2: IPs per-zone (only missing zones) ---
        ip_acc_zones = {}
        for z in zones_to_fetch:
            aid = str(z.get("account_id", ""))
            ip_acc_zones.setdefault(aid, []).append(z)

        results = []
        ip_lock = asyncio.Lock()

        async def _fetch_ips_account(acc_id, zones, api_key, cf_account_id):
            sem = asyncio.Semaphore(10)
            acc_processed = 0

            async def _fetch_one(client, zone):
                nonlocal acc_processed
                zone_id = zone["zone_id"]
                zone_name = zone["name"]
                zone_filter = {"AND": [{"datetime_geq": dt_start, "datetime_leq": dt_end}, {"zoneTag": zone_id}]}
                async with sem:
                    top_ips = []
                    try:
                        resp = await _cf_post(
                            client, "https://api.cloudflare.com/client/v4/graphql",
                            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                            json={"query": ip_query, "variables": {
                                "accountTag": cf_account_id,
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
                                    top_ips = sorted(ip_merged.values(), key=lambda x: x["requests"], reverse=True)
                    except Exception:
                        pass
                    async with ip_lock:
                        results.append({
                            "zoneId": zone_id, "domain": zone_name, "date": sync_date,
                            "topCountries": zone_countries.get(zone_id, []),
                            "topIPs": top_ips,
                            "syncedAt": datetime.now(timezone.utc).isoformat(),
                        })
                        acc_processed += 1

            async with httpx.AsyncClient(timeout=60) as client:
                tasks = [_fetch_one(client, zone) for zone in zones]
                await asyncio.gather(*tasks)
            logger.info(f"[Statistic] IP account {acc_id} ({sync_date}): {acc_processed}/{len(zones)} zones")

        logger.info(f"[Statistic] Step 5b ({sync_date}): Fetching IPs for {len(all_zones)} zones...")
        ip_tasks = [_fetch_ips_account(aid, zones, account_map.get(aid, {}).get("api_key", ""), account_map.get(aid, {}).get("cf_account_id", "")) for aid, zones in ip_acc_zones.items()]
        await asyncio.gather(*ip_tasks)

        # Fill missing zones with no data
        fetched_zone_ids = {r["zoneId"] for r in results}
        for zone in zones_to_fetch:
            if zone["zone_id"] not in fetched_zone_ids:
                results.append({
                    "zoneId": zone["zone_id"], "domain": zone["name"], "date": sync_date,
                    "topCountries": zone_countries.get(zone["zone_id"], []),
                    "topIPs": [],
                    "syncedAt": datetime.now(timezone.utc).isoformat(),
                })
        logger.info(f"[Statistic] Step 5c ({sync_date}): {len(results)} missing zones with chart data")

        if results:
            await db[day_col].insert_many(results)
            await db[day_col].create_index("domain")
            await db[day_col].create_index([("zoneId", 1)], unique=True)
            logger.info(f"[Statistic] Step 6 ({sync_date}): Wrote {len(results)} docs -> {day_col}")
            total_synced += len(results)
        else:
            logger.info(f"[Statistic] Step 6 ({sync_date}): No missing zones to sync")

    logger.info(f"[Statistic] Step 7: Chart done: {total_synced} zones for {'+'.join(dates_to_sync)}")
    await _clear_stat_cache(dates_to_sync)
    return {"code": 200, "data": {"synced": total_synced}, "message": f"Synced chart {'+'.join(dates_to_sync)}: {total_synced} zones"}


@router.post("/sync/month")
async def sync_statistic_month(body: dict):
    """POST /statistic/sync/month - Month sync.
    Past days: skip if already has all zones, fetch+write if missing.
    Today: always DROP and re-sync."""
    month = body.get("month") or datetime.now(timezone.utc).strftime("%Y-%m")
    group_id = body.get("groupId") or ""
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    logger.info(f"[Statistic] Month sync start: month={month}, today={today}")

    import httpx

    db = await get_domain_db()
    accounts = await query_all("SELECT id, api_key, cf_account_id FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")

    zone_id_set = await _get_zone_ids(group_id)
    if zone_id_set is not None and not zone_id_set:
        return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    all_zones = await _get_zones(zone_id_set)
    all_zone_ids = {z["zone_id"] for z in all_zones}
    account_map = {str(a["id"]): {"api_key": a["api_key"], "cf_account_id": a.get("cf_account_id", "")} for a in accounts}
    logger.info(f"[Statistic] Month sync: {len(all_zones)} zones")

    # Build list of days in month
    year, mon = map(int, month.split("-"))
    import calendar
    days_in_month = calendar.monthrange(year, mon)[1]

    # Group zones by account
    acc_zones = {}
    for z in all_zones:
        aid = str(z.get("account_id", ""))
        acc_zones.setdefault(aid, []).append(z)

    table_query = """
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
    total_skipped = 0
    cols = await db.list_collection_names()

    for day in range(1, days_in_month + 1):
        sync_date = f"{year:04d}-{mon:02d}-{day:02d}"
        if sync_date > today:
            break
        day_col = f"{TABLE_COLLECTION}_{sync_date.replace('-', '_')}"

        # Today: always DROP and re-sync
        if sync_date == today:
            if day_col in cols:
                await db[day_col].drop()
                logger.info(f"[Statistic] Month DROP {day_col} (today)")
        else:
            # Past days: check if already has all zones
            if day_col in cols:
                existing_ids = set()
                async for doc in db[day_col].find({}, {"zoneId": 1}):
                    existing_ids.add(doc.get("zoneId"))
                if existing_ids >= all_zone_ids:
                    logger.info(f"[Statistic] Month skip {day_col}: {len(existing_ids)} zones exist")
                    total_skipped += 1
                    continue
                else:
                    logger.info(f"[Statistic] Month {day_col}: {len(existing_ids)}/{len(all_zone_ids)} zones, will fetch missing")

        # Fetch from CF
        logger.info(f"[Statistic] Month fetch {sync_date}: {len(all_zones)} zones")
        cf_data = {}
        lock = asyncio.Lock()

        async def _fetch_account(acc_id, zones, api_key):
            sem = asyncio.Semaphore(10)
            acc_ok = 0

            async def _fetch_one(client, zone):
                nonlocal acc_ok
                zone_id = zone["zone_id"]
                async with sem:
                    try:
                        resp = await _cf_post(
                            client, "https://api.cloudflare.com/client/v4/graphql",
                            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                            json={"query": table_query, "variables": {"zoneTag": zone_id, "date": sync_date}},
                        )
                        if resp.status_code != 200:
                            return
                        data = resp.json()
                        if data.get("errors"):
                            return
                        zones_data = ((data.get("data") or {}).get("viewer") or {}).get("zones") or []
                        if not zones_data:
                            return
                        http_data = zones_data[0].get("httpRequests1dGroups") or []
                        if not http_data:
                            return
                        s = http_data[0].get("sum") or {}
                        u = http_data[0].get("uniq") or {}
                        total = s.get("requests", 0)
                        cached = s.get("cachedRequests", 0)
                        async with lock:
                            cf_data[zone_id] = {
                                "total": total, "cached": cached, "uncached": total - cached,
                                "bandwidth": s.get("bytes", 0), "threats": s.get("threats", 0),
                                "pageViews": s.get("pageViews", 0), "uniqueVisitor": u.get("uniques", 0),
                            }
                            acc_ok += 1
                    except Exception:
                        pass

            async with httpx.AsyncClient(timeout=30) as client:
                tasks = [_fetch_one(client, zone) for zone in zones]
                await asyncio.gather(*tasks)
            logger.info(f"[Statistic] Month Account {acc_id} ({sync_date}): {acc_ok}/{len(zones)} zones")

        acc_tasks = [_fetch_account(aid, zones, account_map.get(aid, {}).get("api_key", "")) for aid, zones in acc_zones.items()]
        await asyncio.gather(*acc_tasks)

        # Build results
        results = []
        synced = 0
        for zone in all_zones:
            zid = zone["zone_id"]
            d = cf_data.get(zid)
            if d:
                results.append({"zoneId": zid, "domain": zone["name"], "date": sync_date, **d,
                                "syncedAt": datetime.now(timezone.utc).isoformat()})
                synced += 1
            else:
                results.append({"zoneId": zid, "domain": zone["name"], "date": sync_date,
                                "total": 0, "cached": 0, "uncached": 0, "bandwidth": 0, "threats": 0,
                                "pageViews": 0, "uniqueVisitor": 0,
                                "syncedAt": datetime.now(timezone.utc).isoformat()})

        if results:
            if sync_date == today:
                # Today: DROP and rewrite
                if day_col in cols:
                    await db[day_col].drop()
                await db[day_col].insert_many(results)
            else:
                # Past days: delete only synced zoneIds, then insert
                synced_ids = [r["zoneId"] for r in results]
                await db[day_col].delete_many({"zoneId": {"$in": synced_ids}})
                await db[day_col].insert_many(results)
            await db[day_col].create_index("domain")
            await db[day_col].create_index([("zoneId", 1)], unique=True)
            logger.info(f"[Statistic] Month wrote {len(results)} docs -> {day_col}")
            total_synced += synced

    logger.info(f"[Statistic] Month done: {total_synced} synced, {total_skipped} skipped")
    # Clear cache for all synced dates
    synced_dates = [f"{year:04d}-{mon:02d}-{d:02d}" for d in range(1, days_in_month + 1) if f"{year:04d}-{mon:02d}-{d:02d}" <= today]
    await _clear_stat_cache(synced_dates)
    return {"code": 200, "data": {"synced": total_synced, "skipped": total_skipped},
            "message": f"Month {month}: {total_synced} synced, {total_skipped} skipped"}


@router.post("/sync/chart/month")
async def sync_statistic_chart_month(body: dict):
    """POST /statistic/sync/chart/month - Month chart sync.
    Same logic: past days skip if complete, today always re-sync."""
    month = body.get("month") or datetime.now(timezone.utc).strftime("%Y-%m")
    group_id = body.get("groupId") or ""
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    logger.info(f"[Statistic] Chart month sync start: month={month}, today={today}")

    import httpx

    db = await get_domain_db()
    accounts = await query_all("SELECT id, api_key, cf_account_id FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")

    zone_id_set = await _get_zone_ids(group_id)
    if zone_id_set is not None and not zone_id_set:
        return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    all_zones = await _get_zones(zone_id_set)
    all_zone_ids = {z["zone_id"] for z in all_zones}
    account_map = {str(a["id"]): {"api_key": a["api_key"], "cf_account_id": a.get("cf_account_id", "")} for a in accounts}
    logger.info(f"[Statistic] Chart month sync: {len(all_zones)} zones")

    year, mon = map(int, month.split("-"))
    import calendar
    days_in_month = calendar.monthrange(year, mon)[1]

    acc_zones = {}
    for z in all_zones:
        aid = str(z.get("account_id", ""))
        acc_zones.setdefault(aid, []).append(z)

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

    total_synced = 0
    total_skipped = 0
    cols = await db.list_collection_names()

    for day in range(1, days_in_month + 1):
        sync_date = f"{year:04d}-{mon:02d}-{day:02d}"
        if sync_date > today:
            break
        day_col = f"{CHART_COLLECTION}_{sync_date.replace('-', '_')}"

        if sync_date == today:
            if day_col in cols:
                await db[day_col].drop()
                logger.info(f"[Statistic] Chart month DROP {day_col} (today)")
        else:
            if day_col in cols:
                existing_ids = set()
                async for doc in db[day_col].find({}, {"zoneId": 1}):
                    existing_ids.add(doc.get("zoneId"))
                if existing_ids >= all_zone_ids:
                    logger.info(f"[Statistic] Chart month skip {day_col}: {len(existing_ids)} zones exist")
                    total_skipped += 1
                    continue
                else:
                    logger.info(f"[Statistic] Chart month {day_col}: {len(existing_ids)}/{len(all_zone_ids)} zones, will fetch")

        dt_start = f"{sync_date}T00:00:00Z"
        dt_end = f"{sync_date}T23:59:59Z"

        # Phase 1: Countries
        zone_countries = {}
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
                    if resp.status_code == 200:
                        data = resp.json()
                        if not data.get("errors"):
                            groups = (((data.get("data") or {}).get("viewer") or {}).get("accounts") or [{}])[0].get("httpRequestsAdaptiveGroups") or []
                            for g in groups:
                                dim = g.get("dimensions") or {}
                                zid = dim.get("zoneTag", "")
                                country = dim.get("clientCountryName", "")
                                if zid and country and country != "XX":
                                    zone_countries.setdefault(zid, []).append({"country": country, "requests": g.get("count", 0)})
                except Exception as e:
                    logger.warning(f"[Statistic] Chart month country {aid} ({sync_date}): {e}")

        for zid in zone_countries:
            zone_countries[zid].sort(key=lambda x: x["requests"], reverse=True)
            zone_countries[zid] = zone_countries[zid][:10]

        # Phase 2: IPs
        ip_acc_zones = {}
        for z in all_zones:
            aid = str(z.get("account_id", ""))
            ip_acc_zones.setdefault(aid, []).append(z)

        results = []
        ip_lock = asyncio.Lock()

        async def _fetch_ips_account(acc_id, zones, api_key, cf_account_id):
            sem = asyncio.Semaphore(10)

            async def _fetch_one(client, zone):
                zone_id = zone["zone_id"]
                zone_name = zone["name"]
                zone_filter = {"AND": [{"datetime_geq": dt_start, "datetime_leq": dt_end}, {"zoneTag": zone_id}]}
                async with sem:
                    top_ips = []
                    try:
                        resp = await _cf_post(
                            client, "https://api.cloudflare.com/client/v4/graphql",
                            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                            json={"query": ip_query, "variables": {
                                "accountTag": cf_account_id,
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
                                    top_ips = sorted(ip_merged.values(), key=lambda x: x["requests"], reverse=True)
                    except Exception:
                        pass
                    async with ip_lock:
                        results.append({
                            "zoneId": zone_id, "domain": zone_name, "date": sync_date,
                            "topCountries": zone_countries.get(zone_id, []),
                            "topIPs": top_ips,
                            "syncedAt": datetime.now(timezone.utc).isoformat(),
                        })

            async with httpx.AsyncClient(timeout=60) as client:
                tasks = [_fetch_one(client, zone) for zone in zones]
                await asyncio.gather(*tasks)

        logger.info(f"[Statistic] Chart month ({sync_date}): fetching IPs...")
        ip_tasks = [_fetch_ips_account(aid, zones, account_map.get(aid, {}).get("api_key", ""), account_map.get(aid, {}).get("cf_account_id", "")) for aid, zones in ip_acc_zones.items()]
        await asyncio.gather(*ip_tasks)

        # Fill missing zones
        fetched_ids = {r["zoneId"] for r in results}
        for zone in all_zones:
            if zone["zone_id"] not in fetched_ids:
                results.append({
                    "zoneId": zone["zone_id"], "domain": zone["name"], "date": sync_date,
                    "topCountries": zone_countries.get(zone["zone_id"], []),
                    "topIPs": [],
                    "syncedAt": datetime.now(timezone.utc).isoformat(),
                })

        if results:
            if sync_date == today:
                # Today: DROP and rewrite
                if day_col in cols:
                    await db[day_col].drop()
                await db[day_col].insert_many(results)
            else:
                # Past days: delete only synced zoneIds, then insert
                synced_ids = [r["zoneId"] for r in results]
                await db[day_col].delete_many({"zoneId": {"$in": synced_ids}})
                await db[day_col].insert_many(results)
            await db[day_col].create_index("domain")
            await db[day_col].create_index([("zoneId", 1)], unique=True)
            logger.info(f"[Statistic] Chart month wrote {len(results)} docs -> {day_col}")
            total_synced += len(results)

    logger.info(f"[Statistic] Chart month done: {total_synced} synced, {total_skipped} skipped")
    synced_dates = [f"{year:04d}-{mon:02d}-{d:02d}" for d in range(1, days_in_month + 1) if f"{year:04d}-{mon:02d}-{d:02d}" <= today]
    await _clear_stat_cache(synced_dates)
    return {"code": 200, "data": {"synced": total_synced, "skipped": total_skipped},
            "message": f"Chart month {month}: {total_synced} synced, {total_skipped} skipped"}


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
