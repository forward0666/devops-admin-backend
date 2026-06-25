import logging
from datetime import datetime, timezone
from fastapi import APIRouter, HTTPException

from app.services.db import query_all
from app.services.mongodb import get_db

logger = logging.getLogger(__name__)
router = APIRouter()

COLLECTION = "domain_statistic"


@router.post("/sync")
async def sync_statistic(body: dict):
    """POST /statistic/sync - Sync zone analytics from CF GraphQL to MongoDB."""
    date = body.get("date") or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    group_id = body.get("groupId") or ""
    logger.info(f"[Statistic] Sync request: date={date}, groupId={group_id or 'all'}")

    import httpx
    from app.config import MONGODB_HOST, MONGODB_PORT, MONGODB_USER, MONGODB_PASSWORD, MONGODB_AUTH_DB
    from motor.motor_asyncio import AsyncIOMotorClient

    db = await get_db()
    accounts = await query_all("SELECT id, api_key FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")

    # Get zoneIds from domain.domain_meta if groupId specified
    zone_id_set = None
    if group_id:
        domain_uri = f"mongodb://{MONGODB_USER}:{MONGODB_PASSWORD}@{MONGODB_HOST}:{MONGODB_PORT}/domain?authSource={MONGODB_AUTH_DB}"
        domain_client = AsyncIOMotorClient(domain_uri)
        meta_cursor = domain_client["domain"]["domain_meta"].find({"groupId": group_id}, {"zoneId": 1})
        meta_list = await meta_cursor.to_list(length=5000)
        zone_id_set = {m["zoneId"] for m in meta_list if m.get("zoneId")}
        domain_client.close()
        logger.info(f"[Statistic] Group {group_id}: {len(zone_id_set)} zone_ids")
        if not zone_id_set:
            return {"code": 200, "data": {"synced": 0}, "message": "No zones in group"}

    # Collect all zones from MongoDB
    cf_db = await get_db()
    all_zones = []
    collections = await cf_db.list_collection_names()
    for col_name in collections:
        if col_name.endswith("_zones"):
            col = cf_db[col_name]
            q = {"zone_id": {"$in": list(zone_id_set)}} if zone_id_set else {}
            async for zone in col.find(q, {"zone_id": 1, "name": 1, "account_id": 1}):
                if zone.get("zone_id") and zone.get("name"):
                    all_zones.append(zone)

    logger.info(f"[Statistic] Syncing {len(all_zones)} zones for date={date}")

    # Build account_id -> api_key map
    account_keys = {str(a["id"]): a["api_key"] for a in accounts}

    # GraphQL query for daily stats (zone-level + country breakdown)
    query = """
    query($zoneTag: String!, $date: String!) {
      viewer {
        zones(filter: { zoneTag: $zoneTag }) {
          totals: httpRequests1dGroups(filter: { date: $date }, limit: 1) {
            sum {
              requests
              cachedRequests
              bytes
              threats
              pageViews
            }
            uniq {
              uniques
            }
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
            account_id = str(zone.get("account_id", ""))
            api_key = account_keys.get(account_id)
            if not api_key:
                continue

            try:
                resp = await client.post(
                    "https://api.cloudflare.com/client/v4/graphql",
                    headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                    json={"query": query, "variables": {"zoneTag": zone_id, "date": date}},
                )
                if resp.status_code != 200:
                    logger.warning(f"[Statistic] {zone_name}: GraphQL returned {resp.status_code}")
                    continue

                data = resp.json()
                if data.get("errors"):
                    logger.warning(f"[Statistic] {zone_name}: GraphQL errors: {data['errors'][:2]}")
                    continue

                viewer = (data.get("data") or {}).get("viewer")
                if not viewer:
                    continue
                zones_data = viewer.get("zones") or []
                if not zones_data:
                    continue

                totals_data = zones_data[0].get("totals") or []
                if not totals_data:
                    continue

                s = totals_data[0].get("sum") or {}
                u = totals_data[0].get("uniq") or {}
                total = s.get("requests", 0)
                cached = s.get("cachedRequests", 0)

                record = {
                    "zoneId": zone_id,
                    "domain": zone_name,
                    "date": date,
                    "total": total,
                    "cached": cached,
                    "uncached": total - cached,
                    "bandwidth": s.get("bytes", 0),
                    "threats": s.get("threats", 0),
                    "pageViews": s.get("pageViews", 0),
                    "uniqueVisitor": u.get("uniques", 0),
                    "syncedAt": datetime.now(timezone.utc).isoformat(),
                }
                results.append(record)
                synced += 1
            except Exception as e:
                logger.warning(f"[Statistic] {zone_name}: {e}")

    # Fetch country + ASN breakdown (account-level, per zone)
    country_query = """
    query($accountTag: String!, $date: String!) {
      viewer {
        accounts(filter: { accountTag: $accountTag }) {
          countries: httpRequests1dGroups(
            filter: { date: $date }
            limit: 1000
            dimensions: [clientCountryName, zoneTag]
          ) {
            sum { requests }
            uniq { uniques }
            dimensions { clientCountryName zoneTag }
          }
          asns: httpRequests1dGroups(
            filter: { date: $date }
            limit: 1000
            dimensions: [clientASN, zoneTag]
          ) {
            sum { requests }
            uniq { uniques }
            dimensions { clientASN zoneTag }
          }
        }
      }
    }
    """

    # Build zone -> account map
    country_by_zone: dict[str, list[dict]] = {}
    asn_by_zone: dict[str, list[dict]] = {}

    async with httpx.AsyncClient(timeout=60) as client:
        for acc in accounts:
            account_id = str(acc["id"])
            api_key = acc["api_key"]
            try:
                resp = await client.post(
                    "https://api.cloudflare.com/client/v4/graphql",
                    headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                    json={"query": country_query, "variables": {"accountTag": account_id, "date": date}},
                )
                if resp.status_code != 200:
                    continue
                data = resp.json()
                if data.get("errors"):
                    logger.warning(f"[Statistic] breakdown query account {account_id}: {data['errors'][:1]}")
                    continue

                accounts_data = ((data.get("data") or {}).get("viewer") or {}).get("accounts") or []
                if not accounts_data:
                    continue

                # Country data
                for g in (accounts_data[0].get("countries") or []):
                    dim = g.get("dimensions") or {}
                    zid = dim.get("zoneTag", "")
                    country = dim.get("clientCountryName", "")
                    if not zid or not country or country == "XX":
                        continue
                    if zone_id_set and zid not in zone_id_set:
                        continue
                    cs = g.get("sum") or {}
                    cu = g.get("uniq") or {}
                    if zid not in country_by_zone:
                        country_by_zone[zid] = []
                    country_by_zone[zid].append({"country": country, "requests": cs.get("requests", 0), "uniqueVisitor": cu.get("uniques", 0)})

                # ASN data
                for g in (accounts_data[0].get("asns") or []):
                    dim = g.get("dimensions") or {}
                    zid = dim.get("zoneTag", "")
                    asn = dim.get("clientASN", 0)
                    if not zid or not asn:
                        continue
                    if zone_id_set and zid not in zone_id_set:
                        continue
                    cs = g.get("sum") or {}
                    if zid not in asn_by_zone:
                        asn_by_zone[zid] = []
                    asn_by_zone[zid].append({"asn": asn, "requests": cs.get("requests", 0)})
            except Exception as e:
                logger.warning(f"[Statistic] breakdown query account {account_id}: {e}")

    # Merge country + ASN data into results
    for r in results:
        zid = r["zoneId"]
        countries = country_by_zone.get(zid, [])
        countries.sort(key=lambda x: x["requests"], reverse=True)
        r["topCountries"] = countries[:10]
        asns = asn_by_zone.get(zid, [])
        asns.sort(key=lambda x: x["requests"], reverse=True)
        r["topAsns"] = asns[:10]

    # Delete old data for this date, then insert new
    if results:
        await db[COLLECTION].delete_many({"date": date})
        await db[COLLECTION].insert_many(results)
        await db[COLLECTION].create_index("date")
        await db[COLLECTION].create_index("zoneId")

    logger.info(f"[Statistic] Done: {synced} zones synced for {date}")
    return {"code": 200, "data": {"synced": synced}, "message": f"Synced {synced} zone stat for {date}"}
