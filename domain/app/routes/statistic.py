import logging
from fastapi import APIRouter, Query

from app.services.mongodb import get_source_db

logger = logging.getLogger(__name__)
router = APIRouter()

COLLECTION = "domain_statistic"


@router.get("")
async def get_statistic(date: str = Query(...)):
    """GET /statistic?date=2026-06-26 - Read stats from MongoDB cloudflare.domain_statistic."""
    db = await get_source_db()
    cursor = db[COLLECTION].find({"date": date}, {"_id": 0, "topCountries": 0}).sort("total", -1)
    rows = await cursor.to_list(length=10000)
    return {"code": 200, "data": rows}


@router.get("/chart")
async def get_statistic_chart(date: str = Query(...)):
    """GET /statistic/chart?date=2026-06-26 - Read chart data (topCountries, topAsns)."""
    db = await get_source_db()
    cursor = db[COLLECTION].find(
        {"date": date},
        {"_id": 0, "zoneId": 1, "domain": 1, "topCountries": 1, "topAsns": 1, "total": 1, "bandwidth": 1, "threats": 1}
    ).sort("total", -1)
    rows = await cursor.to_list(length=10000)
    return {"code": 200, "data": rows}
