import logging
from fastapi import APIRouter, Query

from app.services.mongodb import get_db

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE_COLLECTION = "statistic"
CHART_COLLECTION = "statistic_chart"


@router.get("")
async def get_statistic(date: str = Query(...)):
    """GET /statistic?date=2026-06-26 - Read table stats from domain MongoDB."""
    db = await get_db()
    cursor = db[TABLE_COLLECTION].find({"date": date}, {"_id": 0}).sort("total", -1)
    rows = await cursor.to_list(length=10000)
    return {"code": 200, "data": rows}


@router.get("/chart")
async def get_statistic_chart(date: str = Query(...)):
    """GET /statistic/chart?date=2026-06-26 - Read chart data from domain MongoDB."""
    db = await get_db()
    cursor = db[CHART_COLLECTION].find({"date": date}, {"_id": 0}).sort("domain", 1)
    rows = await cursor.to_list(length=10000)
    return {"code": 200, "data": rows}
