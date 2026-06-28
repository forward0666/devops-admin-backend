import logging
import json
from datetime import datetime, timezone, timedelta
from fastapi import APIRouter, Query

from app.services.mongodb import get_db
from app.services.redis import get_redis

logger = logging.getLogger(__name__)

CACHE_TTL = 3600  # 1 hour
router = APIRouter()


async def _set_cache(cache_key: str, data):
    """Set cache, best effort."""
    try:
        r = await get_redis()
        await r.setex(cache_key, CACHE_TTL, json.dumps(data))
    except Exception:
        pass

TABLE_COLLECTION = "statistic"
CHART_COLLECTION = "statistic_chart"


def _day_col(base: str, date: str) -> str:
    return f"{base}_{date.replace('-', '_')}"


def _month_col(base: str, month: str) -> str:
    return f"{base}_{month.replace('-', '_')}"


async def _get_group_zone_ids(db, group_id: str):
    """Get zoneIds for a group from domain_meta collection."""
    if not group_id or group_id == "all":
        return None
    meta_cursor = db["domain_meta"].find({"groupId": group_id}, {"zoneId": 1})
    meta_list = await meta_cursor.to_list(length=5000)
    zone_ids = {m["zoneId"] for m in meta_list if m.get("zoneId")}
    return zone_ids if zone_ids else set()


@router.get("")
async def get_statistic(date: str = Query(None), month: str = Query(None), year: str = Query(None), groupId: str = Query(None), dateFrom: str = Query(None), dateTo: str = Query(None), monthFrom: str = Query(None), monthTo: str = Query(None)):
    db = await get_db()
    group_zone_ids = await _get_group_zone_ids(db, groupId)
    zone_filter = {"zoneId": {"$in": list(group_zone_ids)}} if group_zone_ids is not None else {}
    if group_zone_ids is not None and not group_zone_ids:
        return {"code": 200, "data": []}

    # Check cache
    cache_key = f"stat:{groupId or 'all'}:{year or ''}:{month or ''}:{date or ''}:{dateFrom or ''}:{dateTo or ''}:{monthFrom or ''}:{monthTo or ''}"
    try:
        r = await get_redis()
        cached = await r.get(cache_key)
        if cached:
            return {"code": 200, "data": json.loads(cached)}
    except Exception:
        pass

    if monthFrom and monthTo:
        # Month range query: merge multiple month collections
        cols = await db.list_collection_names()
        merged = {}
        cur_y, cur_m = map(int, monthFrom.split("-"))
        end_y, end_m = map(int, monthTo.split("-"))
        while (cur_y, cur_m) <= (end_y, end_m):
            m_col = f"{TABLE_COLLECTION}_{cur_y:04d}_{cur_m:02d}"
            prefix = f"{TABLE_COLLECTION}_{cur_y:04d}_{cur_m:02d}_"
            if m_col in cols:
                async for r in db[m_col].find(zone_filter, {"_id": 0}):
                    d = (r.get("domain", "") or "").strip()
                    if not d: continue
                    if d not in merged:
                        merged[d] = {"domain": d, "zoneId": r.get("zoneId", ""), "total": 0, "cached": 0, "uncached": 0, "bandwidth": 0, "threats": 0, "pageViews": 0, "uniqueVisitor": 0}
                    merged[d]["total"] += r.get("total", 0)
                    merged[d]["cached"] += r.get("cached", 0)
                    merged[d]["uncached"] += r.get("uncached", 0)
                    merged[d]["bandwidth"] += r.get("bandwidth", 0)
                    merged[d]["threats"] += r.get("threats", 0)
                    merged[d]["pageViews"] += r.get("pageViews", 0)
                    merged[d]["uniqueVisitor"] += r.get("uniqueVisitor", 0)
            else:
                day_cols = sorted([c for c in cols if c.startswith(prefix)])
                for col_name in day_cols:
                    async for r in db[col_name].find(zone_filter, {"_id": 0}):
                        d = (r.get("domain", "") or "").strip()
                        if not d: continue
                        if d not in merged:
                            merged[d] = {"domain": d, "zoneId": r.get("zoneId", ""), "total": 0, "cached": 0, "uncached": 0, "bandwidth": 0, "threats": 0, "pageViews": 0, "uniqueVisitor": 0}
                        merged[d]["total"] += r.get("total", 0)
                        merged[d]["cached"] += r.get("cached", 0)
                        merged[d]["uncached"] += r.get("uncached", 0)
                        merged[d]["bandwidth"] += r.get("bandwidth", 0)
                        merged[d]["threats"] += r.get("threats", 0)
                        merged[d]["pageViews"] += r.get("pageViews", 0)
                        merged[d]["uniqueVisitor"] += r.get("uniqueVisitor", 0)
            # Advance to next month
            cur_m += 1
            if cur_m > 12:
                cur_m = 1
                cur_y += 1
        rows = sorted(merged.values(), key=lambda x: x["total"], reverse=True)
        await _set_cache(cache_key, rows)
        return {"code": 200, "data": rows}

    if dateFrom and dateTo:
        # Date range query: merge multiple day collections
        cols = await db.list_collection_names()
        merged = {}
        current = datetime.strptime(dateFrom, "%Y-%m-%d")
        end = datetime.strptime(dateTo, "%Y-%m-%d")
        while current <= end:
            d = current.strftime("%Y-%m-%d")
            day_col = f"{TABLE_COLLECTION}_{d.replace('-', '_')}"
            if day_col in cols:
                async for r in db[day_col].find(zone_filter, {"_id": 0}):
                    domain = (r.get("domain", "") or "").strip()
                    if not domain:
                        continue
                    if domain not in merged:
                        merged[domain] = {"domain": domain, "zoneId": r.get("zoneId", ""), "total": 0, "cached": 0, "uncached": 0, "bandwidth": 0, "threats": 0, "pageViews": 0, "uniqueVisitor": 0}
                    merged[domain]["total"] += r.get("total", 0)
                    merged[domain]["cached"] += r.get("cached", 0)
                    merged[domain]["uncached"] += r.get("uncached", 0)
                    merged[domain]["bandwidth"] += r.get("bandwidth", 0)
                    merged[domain]["threats"] += r.get("threats", 0)
                    merged[domain]["pageViews"] += r.get("pageViews", 0)
                    merged[domain]["uniqueVisitor"] += r.get("uniqueVisitor", 0)
            current += timedelta(days=1)
        rows = sorted(merged.values(), key=lambda x: x["total"], reverse=True)
        await _set_cache(cache_key, rows)
        return {"code": 200, "data": rows}

    if year:
        prefix = f"{TABLE_COLLECTION}_{year}_"
        cols = await db.list_collection_names()
        day_cols = sorted([c for c in cols if c.startswith(prefix)])
        logger.info(f"[Statistic] Year {year}: found {len(day_cols)} day collections")
        merged = {}
        for col_name in day_cols:
            async for r in db[col_name].find(zone_filter, {"_id": 0}):
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
        await _set_cache(cache_key, rows)
        return {"code": 200, "data": rows}

    elif month:
        month_col = _month_col(TABLE_COLLECTION, month)
        cols = await db.list_collection_names()
        rows = []
        if month_col in cols:
            cursor = db[month_col].find(zone_filter, {"_id": 0}).sort("total", -1)
            rows = await cursor.to_list(length=10000)
        if not rows:
            prefix = f"{TABLE_COLLECTION}_{month.replace('-', '_')}_"
            day_cols = sorted([c for c in cols if c.startswith(prefix)])
            logger.info(f"[Statistic] Month {month}: found {len(day_cols)} day collections")
            merged = {}
            for col_name in day_cols:
                async for r in db[col_name].find(zone_filter, {"_id": 0}):
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
        await _set_cache(cache_key, rows)
        return {"code": 200, "data": rows}

    elif date:
        day_col = _day_col(TABLE_COLLECTION, date)
        cols = await db.list_collection_names()
        if day_col in cols:
            count = await db[day_col].count_documents(zone_filter)
            logger.info(f"[Statistic] Day {date}: {day_col} has {count} docs")
            cursor = db[day_col].find(zone_filter, {"_id": 0}).sort("total", -1)
            rows = await cursor.to_list(length=10000)
        else:
            logger.info(f"[Statistic] Day {date}: {day_col} not found")
            rows = []
        await _set_cache(cache_key, rows)
        return {"code": 200, "data": rows}

    return {"code": 200, "data": []}


@router.get("/chart")
async def get_statistic_chart(date: str = Query(None), month: str = Query(None), year: str = Query(None), groupId: str = Query(None), dateFrom: str = Query(None), dateTo: str = Query(None), monthFrom: str = Query(None), monthTo: str = Query(None)):
    db = await get_db()
    group_zone_ids = await _get_group_zone_ids(db, groupId)
    zone_filter = {"zoneId": {"$in": list(group_zone_ids)}} if group_zone_ids is not None else {}
    if group_zone_ids is not None and not group_zone_ids:
        return {"code": 200, "data": []}

    # Check cache
    cache_key = f"stat_chart:{groupId or 'all'}:{year or ''}:{month or ''}:{date or ''}:{dateFrom or ''}:{dateTo or ''}:{monthFrom or ''}:{monthTo or ''}"
    try:
        r = await get_redis()
        cached = await r.get(cache_key)
        if cached:
            return {"code": 200, "data": json.loads(cached)}
    except Exception:
        pass

    if monthFrom and monthTo:
        # Month range query for chart
        cols = await db.list_collection_names()
        merged = {}
        cur_y, cur_m = map(int, monthFrom.split("-"))
        end_y, end_m = map(int, monthTo.split("-"))
        while (cur_y, cur_m) <= (end_y, end_m):
            m_col = f"{CHART_COLLECTION}_{cur_y:04d}_{cur_m:02d}"
            prefix = f"{CHART_COLLECTION}_{cur_y:04d}_{cur_m:02d}_"
            if m_col in cols:
                async for r in db[m_col].find(zone_filter, {"_id": 0}):
                    d = (r.get("domain", "") or "").strip()
                    if not d: continue
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
            else:
                day_cols = sorted([c for c in cols if c.startswith(prefix)])
                for col_name in day_cols:
                    async for r in db[col_name].find(zone_filter, {"_id": 0}):
                        d = (r.get("domain", "") or "").strip()
                        if not d: continue
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
            cur_m += 1
            if cur_m > 12:
                cur_m = 1
                cur_y += 1
        result = []
        for d, v in merged.items():
            countries = sorted([{"country": k, "requests": v} for k, v in v["topCountries"].items()], key=lambda x: x["requests"], reverse=True)
            ips = sorted(v["topIPs"].values(), key=lambda x: x["requests"], reverse=True)
            result.append({"domain": d, "zoneId": v["zoneId"], "topCountries": countries[:10], "topIPs": ips})
        result.sort(key=lambda x: x["domain"])
        await _set_cache(cache_key, result)
        return {"code": 200, "data": result}

    if dateFrom and dateTo:
        # Date range query for chart: merge multiple day collections
        cols = await db.list_collection_names()
        merged = {}
        current = datetime.strptime(dateFrom, "%Y-%m-%d")
        end = datetime.strptime(dateTo, "%Y-%m-%d")
        while current <= end:
            d = current.strftime("%Y-%m-%d")
            day_col = f"{CHART_COLLECTION}_{d.replace('-', '_')}"
            if day_col in cols:
                async for r in db[day_col].find(zone_filter, {"_id": 0}):
                    domain = (r.get("domain", "") or "").strip()
                    if not domain:
                        continue
                    if domain not in merged:
                        merged[domain] = {"domain": domain, "zoneId": r.get("zoneId", ""), "topCountries": {}, "topIPs": {}}
                    for c in r.get("topCountries", []):
                        k = c["country"]
                        merged[domain]["topCountries"][k] = merged[domain]["topCountries"].get(k, 0) + c["requests"]
                    for item in r.get("topIPs", []):
                        k = item["ip"]
                        if k in merged[domain]["topIPs"]:
                            merged[domain]["topIPs"][k]["requests"] += item["requests"]
                        else:
                            merged[domain]["topIPs"][k] = {"ip": item["ip"], "country": item.get("country", ""), "requests": item["requests"]}
            current += timedelta(days=1)
        result = []
        for d, v in merged.items():
            countries = sorted([{"country": k, "requests": v} for k, v in v["topCountries"].items()], key=lambda x: x["requests"], reverse=True)
            ips = sorted(v["topIPs"].values(), key=lambda x: x["requests"], reverse=True)
            result.append({"domain": d, "zoneId": v["zoneId"], "topCountries": countries[:10], "topIPs": ips})
        result.sort(key=lambda x: x["domain"])
        await _set_cache(cache_key, result)
        return {"code": 200, "data": result}

    if year:
        prefix = f"{CHART_COLLECTION}_{year}_"
        cols = await db.list_collection_names()
        day_cols = sorted([c for c in cols if c.startswith(prefix)])
        logger.info(f"[Statistic] Chart year {year}: found {len(day_cols)} day collections")
        merged = {}
        for col_name in day_cols:
            async for r in db[col_name].find(zone_filter, {"_id": 0}):
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
        await _set_cache(cache_key, result)
        return {"code": 200, "data": result}

    elif month:
        month_col = _month_col(CHART_COLLECTION, month)
        cols = await db.list_collection_names()
        rows = []
        if month_col in cols:
            cursor = db[month_col].find(zone_filter, {"_id": 0}).sort("domain", 1)
            rows = await cursor.to_list(length=10000)
        if not rows:
            prefix = f"{CHART_COLLECTION}_{month.replace('-', '_')}_"
            day_cols = sorted([c for c in cols if c.startswith(prefix)])
            logger.info(f"[Statistic] Chart month {month}: found {len(day_cols)} day collections")
            merged = {}
            for col_name in day_cols:
                count = await db[col_name].count_documents({})
                logger.info(f"[Statistic]   {col_name}: {count} docs")
                async for r in db[col_name].find(zone_filter, {"_id": 0}):
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
        await _set_cache(cache_key, rows)
        return {"code": 200, "data": rows}

    elif date:
        day_col = _day_col(CHART_COLLECTION, date)
        cols = await db.list_collection_names()
        if day_col in cols:
            count = await db[day_col].count_documents(zone_filter)
            logger.info(f"[Statistic] Chart day {date}: {day_col} has {count} docs")
            cursor = db[day_col].find(zone_filter, {"_id": 0}).sort("domain", 1)
            rows = await cursor.to_list(length=10000)
        else:
            logger.info(f"[Statistic] Chart day {date}: {day_col} not found")
            rows = []
        await _set_cache(cache_key, rows)
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


