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

    # Collect zones from MongoDB
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

    if not all_zones:
        raise HTTPException(status_code=400, detail="No zones synced. Please sync zones first.")

    logger.info(f"[Statistic] Syncing {len(all_zones)} zones for date={date}")

    # Build account_id -> api_key map
    account_keys = {str(a["id"]): a["api_key"] for a in accounts}

    # GraphQL query for daily stats
    query = """
    query($zoneTag: String!, $date: String!) {
      viewer {
        zones(filter: { zoneTag: $zoneTag }) {
          httpRequests1dGroups(filter: { date: $date }, limit: 1) {
            sum {
              requests
              cachedRequests
              uncachedRequests
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
                viewer = (data.get("data") or {}).get("viewer")
                if not viewer:
                    continue
                zones_data = viewer.get("zones") or []
                if not zones_data:
                    continue

                http_data = zones_data[0].get("httpRequests1dGroups") or []
                if not http_data:
                    continue

                s = http_data[0].get("sum") or {}
                u = http_data[0].get("uniq") or {}
                record = {
                    "zoneId": zone_id,
                    "domain": zone_name,
                    "date": date,
                    "total": s.get("requests", 0),
                    "cached": s.get("cachedRequests", 0),
                    "uncached": s.get("uncachedRequests", 0),
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

    # Delete old data for this date, then insert new
    if results:
        await db[COLLECTION].delete_many({"date": date})
        await db[COLLECTION].insert_many(results)
        await db[COLLECTION].create_index("date")
        await db[COLLECTION].create_index("zoneId")

    logger.info(f"[Statistic] Done: {synced} zones synced for {date}")
    return {"code": 200, "data": {"synced": synced}, "message": f"Synced {synced} zone stats for {date}"}
