import logging
import time
from fastapi import APIRouter, Query

from app.services.mongodb import get_db

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE_COLLECTION = "statistic"
CHART_COLLECTION = "statistic_chart"

_cache: dict = {}
CACHE_TTL = 60


def _cache_get(key: str):
    if key in _cache:
        ts, data = _cache[key]
        if time.time() - ts < CACHE_TTL:
            return data
        del _cache[key]
    return None


def _cache_set(key: str, data):
    _cache[key] = (time.time(), data)


def _cache_clear():
    _cache.clear()


def _day_col(base: str, date: str) -> str:
    return f"{base}_{date.replace('-', '_')}"


@router.get("")
async def get_statistic(date: str = Query(None), month: str = Query(None)):
    db = await get_db()

    if month:
        cache_key = f"stat:{month}"
        cached = _cache_get(cache_key)
        if cached is not None:
            return {"code": 200, "data": cached}

        prefix = f"{TABLE_COLLECTION}_{month.replace('-', '_')}_"
        cols = await db.list_collection_names()
        day_cols = sorted([c for c in cols if c.startswith(prefix)])
        merged = {}
        for col_name in day_cols:
            async for r in db[col_name].find({}, {"_id": 0}):
                d = r.get("domain", "")
                if d not in merged:
                    merged[d] = {"domain": d, "zoneId": r.get("zoneId", ""), "total": 0, "cached": 0, "uncached": 0, "bandwidth": 0, "threats": 0, "pageViews": 0, "uniqueVisitor": 0}
                merged[d]["total"] += r.get("total", 0)
                merged[d]["cached"] += r.get("cached", 0)
                merged[d]["uncached"] += r.get("uncached", 0)
                merged[d]["bandwidth"] += r.get("bandwidth", 0)
                merged[d]["threats"] += r.get("threats", 0)
                merged[d]["pageViews"] += r.get("pageViews", 0)
                merged[d]["uniqueVisitor"] += r.get("uniqueVisitor", 0)
        rows = sorted(merged.values(), key=lambda x: x["total"], reverse=True)
        _cache_set(cache_key, rows)
        return {"code": 200, "data": rows}

    elif date:
        cache_key = f"stat:{date}"
        cached = _cache_get(cache_key)
        if cached is not None:
            return {"code": 200, "data": cached}

        day_col = _day_col(TABLE_COLLECTION, date)
        cols = await db.list_collection_names()
        if day_col in cols:
            cursor = db[day_col].find({}, {"_id": 0}).sort("total", -1)
            rows = await cursor.to_list(length=10000)
        else:
            rows = []
        _cache_set(cache_key, rows)
        return {"code": 200, "data": rows}

    return {"code": 200, "data": []}


@router.get("/chart")
async def get_statistic_chart(date: str = Query(None), month: str = Query(None)):
    db = await get_db()

    if month:
        cache_key = f"chart:{month}"
        cached = _cache_get(cache_key)
        if cached is not None:
            return {"code": 200, "data": cached}

        prefix = f"{CHART_COLLECTION}_{month.replace('-', '_')}_"
        cols = await db.list_collection_names()
        day_cols = sorted([c for c in cols if c.startswith(prefix)])
        merged = {}
        for col_name in day_cols:
            async for r in db[col_name].find({}, {"_id": 0}):
                d = r.get("domain", "")
                if d not in merged:
                    merged[d] = {"domain": d, "zoneId": r.get("zoneId", ""), "topCountries": {}, "topIPs": {}}
                for c in r.get("topCountries", []):
                    k = c["country"]
                    merged[d]["topCountries"][k] = merged[d]["topCountries"].get(k, 0) + c["requests"]
                for item in r.get("topIPs", []):
                    k = item["ip"]
                    if k in merged[d]["topIPs"]:
                        merged[d]["topIPs"][k]["requests"] += item["requests"]
                    else:
                        merged[d]["topIPs"][k] = {"ip": item["ip"], "country": item.get("country", ""), "requests": item["requests"]}
        result = []
        for d, v in merged.items():
            countries = sorted([{"country": k, "requests": v} for k, v in v["topCountries"].items()], key=lambda x: x["requests"], reverse=True)
            ips = sorted(v["topIPs"].values(), key=lambda x: x["requests"], reverse=True)
            result.append({"domain": d, "zoneId": v["zoneId"], "topCountries": countries[:10], "topIPs": ips[:50]})
        result.sort(key=lambda x: x["domain"])
        _cache_set(cache_key, result)
        return {"code": 200, "data": result}

    elif date:
        cache_key = f"chart:{date}"
        cached = _cache_get(cache_key)
        if cached is not None:
            return {"code": 200, "data": cached}

        day_col = _day_col(CHART_COLLECTION, date)
        cols = await db.list_collection_names()
        if day_col in cols:
            cursor = db[day_col].find({}, {"_id": 0}).sort("domain", 1)
            rows = await cursor.to_list(length=10000)
        else:
            rows = []
        _cache_set(cache_key, rows)
        return {"code": 200, "data": rows}

    return {"code": 200, "data": []}


@router.post("/cache/clear")
async def clear_cache():
    _cache_clear()
    return {"code": 200, "message": "Cache cleared"}
