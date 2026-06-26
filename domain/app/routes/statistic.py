import logging
import json
from fastapi import APIRouter, Query

from app.services.mongodb import get_db
from app.services.redis import get_redis

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE_COLLECTION = "statistic"
CHART_COLLECTION = "statistic_chart"
CACHE_TTL = 60
CACHE_PREFIX = "stat:"


async def _cache_get(key: str):
    r = await get_redis()
    val = await r.get(f"{CACHE_PREFIX}{key}")
    if val:
        return json.loads(val)
    return None


async def _cache_set(key: str, data, ttl: int = CACHE_TTL):
    r = await get_redis()
    await r.set(f"{CACHE_PREFIX}{key}", json.dumps(data, ensure_ascii=False), ex=ttl)


async def _cache_clear(pattern: str = "*"):
    r = await get_redis()
    keys = []
    async for k in r.scan_iter(f"{CACHE_PREFIX}{pattern}"):
        keys.append(k)
    if keys:
        await r.delete(*keys)
        logger.info(f"[Cache] Cleared {len(keys)} keys")
    return len(keys)


def _day_col(base: str, date: str) -> str:
    return f"{base}_{date.replace('-', '_')}"


def _month_col(base: str, month: str) -> str:
    return f"{base}_{month.replace('-', '_')}"


@router.get("")
async def get_statistic(date: str = Query(None), month: str = Query(None), year: str = Query(None)):
    db = await get_db()

    if year:
        cached = await _cache_get(f"stat:{year}")
        if cached is not None:
            return {"code": 200, "data": cached}

        prefix = f"{TABLE_COLLECTION}_{year}_"
        cols = await db.list_collection_names()
        day_cols = sorted([c for c in cols if c.startswith(prefix)])
        logger.info(f"[Statistic] Year {year}: found {len(day_cols)} day collections")
        merged = {}
        for col_name in day_cols:
            async for r in db[col_name].find({}, {"_id": 0}):
                d = (r.get("domain", "") or "").strip()
                if not d:
                    continue
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
        await _cache_set(f"stat:{year}", rows)
        return {"code": 200, "data": rows}

    elif month:
        cached = await _cache_get(f"stat:{month}")
        if cached is not None:
            return {"code": 200, "data": cached}

        month_col = _month_col(TABLE_COLLECTION, month)
        cols = await db.list_collection_names()
        if month_col in cols:
            cursor = db[month_col].find({}, {"_id": 0}).sort("total", -1)
            rows = await cursor.to_list(length=10000)
        else:
            prefix = f"{TABLE_COLLECTION}_{month.replace('-', '_')}_"
            day_cols = sorted([c for c in cols if c.startswith(prefix)])
            logger.info(f"[Statistic] Month {month}: found {len(day_cols)} day collections")
            merged = {}
            for col_name in day_cols:
                count = await db[col_name].count_documents({})
                logger.info(f"[Statistic]   {col_name}: {count} docs")
                async for r in db[col_name].find({}, {"_id": 0}):
                    d = (r.get("domain", "") or "").strip()
                    if not d:
                        continue
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
        await _cache_set(f"stat:{month}", rows)
        return {"code": 200, "data": rows}

    elif date:
        cached = await _cache_get(f"stat:{date}")
        if cached is not None:
            return {"code": 200, "data": cached}

        day_col = _day_col(TABLE_COLLECTION, date)
        cols = await db.list_collection_names()
        if day_col in cols:
            count = await db[day_col].count_documents({})
            logger.info(f"[Statistic] Day {date}: {day_col} has {count} docs")
            cursor = db[day_col].find({}, {"_id": 0}).sort("total", -1)
            rows = await cursor.to_list(length=10000)
        else:
            logger.info(f"[Statistic] Day {date}: {day_col} not found")
            rows = []
        await _cache_set(f"stat:{date}", rows)
        return {"code": 200, "data": rows}

    return {"code": 200, "data": []}


@router.get("/chart")
async def get_statistic_chart(date: str = Query(None), month: str = Query(None), year: str = Query(None)):
    db = await get_db()

    if year:
        cached = await _cache_get(f"chart:{year}")
        if cached is not None:
            return {"code": 200, "data": cached}

        prefix = f"{CHART_COLLECTION}_{year}_"
        cols = await db.list_collection_names()
        day_cols = sorted([c for c in cols if c.startswith(prefix)])
        logger.info(f"[Statistic] Chart year {year}: found {len(day_cols)} day collections")
        merged = {}
        for col_name in day_cols:
            async for r in db[col_name].find({}, {"_id": 0}):
                d = (r.get("domain", "") or "").strip()
                if not d:
                    continue
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
        await _cache_set(f"chart:{year}", result)
        return {"code": 200, "data": result}

    elif month:
        cached = await _cache_get(f"chart:{month}")
        if cached is not None:
            return {"code": 200, "data": cached}

        month_col = _month_col(CHART_COLLECTION, month)
        cols = await db.list_collection_names()
        if month_col in cols:
            cursor = db[month_col].find({}, {"_id": 0}).sort("domain", 1)
            rows = await cursor.to_list(length=10000)
        else:
            prefix = f"{CHART_COLLECTION}_{month.replace('-', '_')}_"
            day_cols = sorted([c for c in cols if c.startswith(prefix)])
            logger.info(f"[Statistic] Chart month {month}: found {len(day_cols)} day collections")
            merged = {}
            for col_name in day_cols:
                count = await db[col_name].count_documents({})
                logger.info(f"[Statistic]   {col_name}: {count} docs")
                async for r in db[col_name].find({}, {"_id": 0}):
                    d = (r.get("domain", "") or "").strip()
                    if not d:
                        continue
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
            rows = result
        await _cache_set(f"chart:{month}", rows)
        return {"code": 200, "data": rows}

    elif date:
        cached = await _cache_get(f"chart:{date}")
        if cached is not None:
            return {"code": 200, "data": cached}

        day_col = _day_col(CHART_COLLECTION, date)
        cols = await db.list_collection_names()
        if day_col in cols:
            count = await db[day_col].count_documents({})
            logger.info(f"[Statistic] Chart day {date}: {day_col} has {count} docs")
            cursor = db[day_col].find({}, {"_id": 0}).sort("domain", 1)
            rows = await cursor.to_list(length=10000)
        else:
            logger.info(f"[Statistic] Chart day {date}: {day_col} not found")
            rows = []
        await _cache_set(f"chart:{date}", rows)
        return {"code": 200, "data": rows}

    return {"code": 200, "data": []}


@router.get("/debug")
async def debug_statistic(date: str = Query(None), month: str = Query(None)):
    db = await get_db()
    cols = await db.list_collection_names()
    result = {}
    if month:
        prefix_t = f"{TABLE_COLLECTION}_{month.replace('-', '_')}_"
        prefix_c = f"{CHART_COLLECTION}_{month.replace('-', '_')}_"
        table_cols = sorted([c for c in cols if c.startswith(prefix_t)])
        chart_cols = sorted([c for c in cols if c.startswith(prefix_c)])
        days = []
        for tc in table_cols:
            date_str = tc.replace(TABLE_COLLECTION + "_", "")
            cc = f"{CHART_COLLECTION}_{date_str}"
            t_count = await db[tc].count_documents({})
            c_count = await db[cc].count_documents({}) if cc in cols else 0
            days.append({"date": date_str, "table": t_count, "chart": c_count})
        result["month"] = month
        result["days"] = days
        result["total_table_days"] = len(table_cols)
        result["total_chart_days"] = len(chart_cols)
    elif date:
        table_col = _day_col(TABLE_COLLECTION, date)
        chart_col = _day_col(CHART_COLLECTION, date)
        result["table_col"] = table_col
        result["table_exists"] = table_col in cols
        result["table_count"] = await db[table_col].count_documents({}) if table_col in cols else 0
        result["chart_col"] = chart_col
        result["chart_exists"] = chart_col in cols
        result["chart_count"] = await db[chart_col].count_documents({}) if chart_col in cols else 0
    result["all_statistic_cols"] = [c for c in cols if c.startswith("statistic")]
    return {"code": 200, "data": result}


@router.post("/cache/clear")
async def clear_cache():
    count = await _cache_clear()
    return {"code": 200, "message": f"Cleared {count} cache keys"}
