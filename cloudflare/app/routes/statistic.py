import logging
from datetime import datetime, timezone
from fastapi import APIRouter, HTTPException, Query

from app.services.db import query_all
from app.services.mongodb import get_db

logger = logging.getLogger(__name__)
router = APIRouter()

COLLECTION = "domain_statistic"


@router.get("")
async def get_statistic(date: str = Query(...)):
    """GET /statistic?date=2026-06-26 - Read cached stats from MongoDB."""
    db = await get_db()
    cursor = db[COLLECTION].find({"date": date}, {"_id": 0}).sort("total", -1)
    rows = await cursor.to_list(length=10000)
    return {"code": 200, "data": rows}


@router.post("/sync")
async def sync_statistic(body: dict):
    """POST /statistic/sync - Sync zone analytics from CF GraphQL to MongoDB."""
    date = body.get("date") or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    if not date:
        raise HTTPException(status_code=400, detail="date is required")

    import httpx

    db = await get_db()
    accounts = await query_all("SELECT id, api_key FROM account")
    if not accounts:
        raise HTTPException(status_code=400, detail="No CF accounts found")

    # Collect all zones from MongoDB
    cf_db = await get_db()
    all_zones = []
    collections = await cf_db.list_collection_names()
    for col_name in collections:
        if col_name.endswith("_zones"):
            col = cf_db[col_name]
            async for zone in col.find({}, {"zone_id": 1, "name": 1, "account_id": 1}):
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
                zones_data = data.get("data", {}).get("viewer", {}).get("zones", [])
                if not zones_data:
                    continue

                http_data = zones_data[0].get("httpRequests1dGroups", [])
                if not http_data:
                    continue

                s = http_data[0].get("sum", {})
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
