import logging
from datetime import datetime, timezone
from fastapi import APIRouter, HTTPException

from app.services.db import query_all
from app.services.mongodb import get_db

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE_COLLECTION = "domain_statistic"
CHART_COLLECTION = "domain_statistic_chart"


@router.post("/sync")
async def sync_statistic(body: dict):
    """POST /statistic/sync - Sync zone basic stats from CF GraphQL to MongoDB."""
    date = body.get("date") or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    group_id = body.get("groupId") or ""
    logger.info(f"[Statistic] Sync request: date={date}, groupId={group_id or 'all'}")

    import httpx

    db = await get_db()
    accounts = await query_all("SELECT id, api_key FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")

    zone_id_set = _get_zone_ids(group_id)
    if zone_id_set is not None and not zone_id_set:
        return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    all_zones = _get_zones(db, zone_id_set)
    logger.info(f"[Statistic] Syncing {len(all_zones)} zones for date={date}")

    account_keys = {str(a["id"]): a["api_key"] for a in accounts}

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
            api_key = account_keys.get(str(zone.get("account_id", "")))
            if not api_key:
                continue
            try:
                resp = await client.post(
                    "https://api.cloudflare.com/client/v4/graphql",
                    headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
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
        await db[TABLE_COLLECTION].delete_many({"date": date})
        await db[TABLE_COLLECTION].insert_many(results)
        await db[TABLE_COLLECTION].create_index("date")
        await db[TABLE_COLLECTION].create_index("zoneId")

    logger.info(f"[Statistic] Done: {synced} zones synced for {date}")
    return {"code": 200, "data": {"synced": synced}, "message": f"Synced {synced} zone stat for {date}"}


@router.post("/sync/chart")
async def sync_statistic_chart(body: dict):
    """POST /statistic/sync/chart - Sync country breakdown from CF to MongoDB."""
    date = body.get("date") or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    group_id = body.get("groupId") or ""
    logger.info(f"[Statistic] Chart sync: date={date}, groupId={group_id or 'all'}")

    import httpx

    db = await get_db()
    accounts = await query_all("SELECT id, api_key FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")

    zone_id_set = _get_zone_ids(group_id)
    if zone_id_set is not None and not zone_id_set:
        return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    all_zones = _get_zones(db, zone_id_set)
    logger.info(f"[Statistic] Chart: {len(all_zones)} zones for date={date}")

    account_keys = {str(a["id"]): a["api_key"] for a in accounts}
    dt_start = f"{date}T00:00:00Z"
    dt_end = f"{date}T23:59:59Z"

    country_query = """
    query($accountTag: String!, $filter: ZoneHttpRequestsAdaptiveGroupsFilter_InputObject) {
      viewer {
        accounts(filter: { accountTag: $accountTag }) {
          series: httpRequestsAdaptiveGroups(limit: 5000, filter: $filter) {
            sum { requests }
            dimensions { clientCountryName }
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
            api_key = account_keys.get(str(zone.get("account_id", "")))
            if not api_key:
                continue
            try:
                resp = await client.post(
                    "https://api.cloudflare.com/client/v4/graphql",
                    headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                    json={
                        "query": country_query,
                        "variables": {
                            "accountTag": str(zone.get("account_id", "")),
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
                    cs = s.get("sum") or {}
                    top_countries.append({"country": country, "requests": cs.get("requests", 0)})

                top_countries.sort(key=lambda x: x["requests"], reverse=True)
                results.append({
                    "zoneId": zone_id, "domain": zone_name, "date": date,
                    "topCountries": top_countries[:10],
                    "syncedAt": datetime.now(timezone.utc).isoformat(),
                })
            except Exception as e:
                logger.warning(f"[Statistic] chart {zone_name}: {e}")

    if results:
        await db[CHART_COLLECTION].delete_many({"date": date})
        await db[CHART_COLLECTION].insert_many(results)
        await db[CHART_COLLECTION].create_index("date")
        await db[CHART_COLLECTION].create_index("zoneId")

    logger.info(f"[Statistic] Chart done: {len(results)} zones for {date}")
    return {"code": 200, "data": {"synced": len(results)}, "message": f"Synced {len(results)} chart data for {date}"}


def _get_zone_ids(group_id: str):
    """Get zoneIds from domain.domain_meta for a group."""
    if not group_id:
        return None
    from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_AUTH_DB
    from motor.motor_asyncio import AsyncIOMotorClient
    import asyncio

    async def _query():
        uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/domain?authSource={MONGODB_AUTH_DB}"
        client = AsyncIOMotorClient(uri)
        meta_cursor = client["domain"]["domain_meta"].find({"groupId": group_id}, {"zoneId": 1})
        meta_list = await meta_cursor.to_list(length=5000)
        client.close()
        return {m["zoneId"] for m in meta_list if m.get("zoneId")}

    # Run async in sync context
    try:
        loop = asyncio.get_event_loop()
        if loop.is_running():
            import concurrent.futures
            with concurrent.futures.ThreadPoolExecutor() as pool:
                return pool.submit(asyncio.run, _query()).result()
        return loop.run_until_complete(_query())
    except Exception:
        return asyncio.run(_query())


def _get_zones(db, zone_id_set):
    """Collect zones from MongoDB."""
    import asyncio

    async def _query():
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

    try:
        loop = asyncio.get_event_loop()
        if loop.is_running():
            import concurrent.futures
            with concurrent.futures.ThreadPoolExecutor() as pool:
                return pool.submit(asyncio.run, _query()).result()
        return loop.run_until_complete(_query())
    except Exception:
        return asyncio.run(_query())
