package com.backend.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings("unchecked")
public class StatisticService {

    private static final String TABLE_COLLECTION = "statistic";
    private static final String CHART_COLLECTION = "statistic_chart";

    @Autowired
    private MongoTemplate mongoTemplate;

    private Set<String> getGroupZoneIds(String groupId) {
        if (groupId == null || groupId.isBlank() || "all".equalsIgnoreCase(groupId)) return null;
        var query = Query.query(Criteria.where("groupId").is(groupId));
        query.fields().include("zoneId").exclude("_id");
        var metas = mongoTemplate.find(query, Map.class, "domain_meta");
        if (metas.isEmpty()) return Collections.emptySet();
        return metas.stream()
                .map(m -> (String) m.get("zoneId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Criteria zoneCriteria(String groupId) {
        Set<String> zoneIds = getGroupZoneIds(groupId);
        if (zoneIds == null) return null;
        if (zoneIds.isEmpty()) return Criteria.where("_id").is("__none__");
        return Criteria.where("zoneId").in(zoneIds.stream().toList());
    }

    // ======================== Public API ========================

    public List<Map<String, Object>> getStatistic(String date, String month, String year,
                                                  String groupId, String dateFrom, String dateTo,
                                                  String monthFrom, String monthTo) {
        if (monthFrom != null && monthTo != null)
            return mergeMonthRange(TABLE_COLLECTION, monthFrom, monthTo, groupId);
        if (dateFrom != null && dateTo != null)
            return aggregateByDomain(TABLE_COLLECTION, dateFrom, dateTo, groupId);
        if (year != null)
            return aggregateYearTable(year, groupId);
        if (month != null)
            return getMonthTable(month, groupId);
        if (date != null)
            return getDayData(TABLE_COLLECTION, date, groupId);
        return Collections.emptyList();
    }

    public List<Map<String, Object>> getChart(String date, String month, String year,
                                              String groupId, String dateFrom, String dateTo,
                                              String monthFrom, String monthTo) {
        if (monthFrom != null && monthTo != null)
            return mergeMonthRangeChart(monthFrom, monthTo, groupId);
        if (dateFrom != null && dateTo != null)
            return aggregateChart(CHART_COLLECTION, dateFrom, dateTo, groupId);
        if (year != null)
            return aggregateYearChart(year, groupId);
        if (month != null)
            return getMonthChart(month, groupId);
        if (date != null)
            return getDayData(CHART_COLLECTION, date, groupId);
        return Collections.emptyList();
    }

    public Map<String, Object> debug(String date, String month) {
        var cols = mongoTemplate.getCollectionNames();
        Map<String, Object> result = new HashMap<>();

        if (month != null) {
            var days = new ArrayList<Map<String, Object>>();
            for (var c : cols) {
                if (c.startsWith(TABLE_COLLECTION + "_" + month.replace("-", "_") + "_")) {
                    String dateStr = c.replace(TABLE_COLLECTION + "_", "");
                    String chartCol = CHART_COLLECTION + "_" + dateStr;
                    Map<String, Object> day = new HashMap<>();
                    day.put("date", dateStr);
                    day.put("tableCount", mongoTemplate.getCollection(chartCol).countDocuments());
                    days.add(day);
                }
            }
            result.put("month", month);
            result.put("days", days);
        } else if (date != null) {
            String dayCol = TABLE_COLLECTION + "_" + date.replace("-", "_");
            String chartCol = CHART_COLLECTION + "_" + date.replace("-", "_");
            result.put("tableExists", cols.contains(dayCol));
            if (cols.contains(dayCol)) result.put("tableCount", mongoTemplate.getCollection(dayCol).countDocuments());
            result.put("chartExists", cols.contains(chartCol));
            if (cols.contains(chartCol)) result.put("chartCount", mongoTemplate.getCollection(chartCol).countDocuments());
        }
        result.put("allStatisticCols", cols.stream().filter(c -> c.startsWith("statistic")).collect(Collectors.toList()));
        return result;
    }

    // ======================== Single Day ========================

    private List<Map<String, Object>> getDayData(String base, String date, String groupId) {
        String colName = base + "_" + date.replace("-", "_");
        if (!mongoTemplate.collectionExists(colName)) return Collections.emptyList();
        var zoneFilter = zoneCriteria(groupId);
        Query query = zoneFilter != null ? Query.query(zoneFilter) : new Query();
        query.with(Sort.by(Sort.Direction.DESC, "total"));
        return (List<Map<String, Object>>) (List<?>) mongoTemplate.find(query, Map.class, colName);
    }

    // ======================== Table Aggregation (MongoDB $group) ========================

    /**
     * Aggregate daily table data across a date range using MongoDB $group, 
     * then combine results from multiple collections in Java (no in-memory per-doc iteration).
     */
    private List<Map<String, Object>> aggregateByDomain(String base, String dateFrom, String dateTo, String groupId) {
        var dateStrs = resolveDateCollections(base, dateFrom, dateTo);
        if (dateStrs.isEmpty()) return Collections.emptyList();
        return runTableAggregation(dateStrs, groupId);
    }

    private List<Map<String, Object>> getMonthTable(String month, String groupId) {
        String mCol = TABLE_COLLECTION + "_" + month.replace("-", "_");
        if (mongoTemplate.collectionExists(mCol)) {
            var zoneFilter = zoneCriteria(groupId);
            Query query = zoneFilter != null ? Query.query(zoneFilter) : new Query();
            query.with(Sort.by(Sort.Direction.DESC, "total"));
            return (List<Map<String, Object>>) (List<?>) mongoTemplate.find(query, Map.class, mCol);
        }
        // Aggregate from day collections using $group
        var cols = mongoTemplate.getCollectionNames();
        var prefix = TABLE_COLLECTION + "_" + month.replace("-", "_") + "_";
        var dayCols = cols.stream().filter(c -> c.startsWith(prefix)).sorted().collect(Collectors.toList());
        return runTableAggregation(dayCols, groupId);
    }

    private List<Map<String, Object>> aggregateYearTable(String year, String groupId) {
        var cols = mongoTemplate.getCollectionNames();
        var prefix = TABLE_COLLECTION + "_" + year + "_";
        var dayCols = cols.stream().filter(c -> c.startsWith(prefix)).sorted().collect(Collectors.toList());
        log.info("[Statistic] Year {} table: {} day collections", year, dayCols.size());
        return runTableAggregation(dayCols, groupId);
    }

    private List<Map<String, Object>> mergeMonthRange(String base, String monthFrom, String monthTo, String groupId) {
        var dateStrs = resolveMonthRangeCollections(base, monthFrom, monthTo);
        return runTableAggregation(dateStrs, groupId);
    }

    /**
     * Core aggregation: run MongoDB $group on each day collection, then combine results.
     * Instead of one massive in-memory merge of all docs, we use $group on each collection
     * (which runs server-side) and only merge the aggregated results (much fewer records).
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> runTableAggregation(List<String> colNames, String groupId) {
        var zoneFilter = zoneCriteria(groupId);
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

        for (String colName : colNames) {
            var pipeline = new ArrayList<AggregationOperation>();

            // Match stage
            if (zoneFilter != null) {
                pipeline.add(Aggregation.match(zoneFilter));
            }

            // Group stage - sum all numeric fields by domain
            pipeline.add(Aggregation.group("domain")
                    .first("zoneId").as("zoneId")
                    .sum("total").as("total")
                    .sum("cached").as("cached")
                    .sum("uncached").as("uncached")
                    .sum("bandwidth").as("bandwidth")
                    .sum("threats").as("threats")
                    .sum("pageViews").as("pageViews")
                    .sum("uniqueVisitor").as("uniqueVisitor"));

            // Sort descending by total
            pipeline.add(Aggregation.sort(Sort.by(Sort.Direction.DESC, "total")));

            var aggregation = Aggregation.newAggregation(pipeline);
            var results = mongoTemplate.aggregate(aggregation, colName, Map.class).getMappedResults();

            for (var doc : results) {
                String domain = ((String) doc.getOrDefault("_id", "")).strip();
                if (domain.isBlank()) continue;
                merged.computeIfAbsent(domain, k -> {
                    var m = new HashMap<String, Object>();
                    m.put("domain", domain);
                    m.put("zoneId", doc.getOrDefault("zoneId", ""));
                    m.put("total", 0L); m.put("cached", 0L); m.put("uncached", 0L);
                    m.put("bandwidth", 0L); m.put("threats", 0L); m.put("pageViews", 0L); m.put("uniqueVisitor", 0L);
                    return m;
                });
                var acc = merged.get(domain);
                for (String f : List.of("total", "cached", "uncached", "bandwidth", "threats", "pageViews", "uniqueVisitor")) {
                    acc.put(f, ((Number) acc.get(f)).longValue() + ((Number) doc.getOrDefault(f, 0)).longValue());
                }
            }
        }

        var result = new ArrayList<>(merged.values());
        result.sort((a, b) -> Long.compare(((Number) b.get("total")).longValue(), ((Number) a.get("total")).longValue()));
        return result;
    }

    // ======================== Chart Aggregation (MongoDB $unwind + $group) ========================

    private List<Map<String, Object>> aggregateChart(String base, String dateFrom, String dateTo, String groupId) {
        var dateStrs = resolveDateCollections(base, dateFrom, dateTo);
        // Chart data has nested arrays (topCountries, topIPs)
        // Per day collection: {domain, zoneId, topCountries: [{country, requests}], topIPs: [{ip, country, requests}]}
        // We need to merge: same domain → sum country/IP requests across days
        return runChartAggregation(dateStrs, groupId);
    }

    private List<Map<String, Object>> getMonthChart(String month, String groupId) {
        String mCol = CHART_COLLECTION + "_" + month.replace("-", "_");
        var cols = mongoTemplate.getCollectionNames();
        if (cols.contains(mCol)) {
            var zoneFilter = zoneCriteria(groupId);
            Query query = zoneFilter != null ? Query.query(zoneFilter) : new Query();
            return (List<Map<String, Object>>) (List<?>) mongoTemplate.find(query, Map.class, mCol);
        }
        var prefix = CHART_COLLECTION + "_" + month.replace("-", "_") + "_";
        var dayCols = cols.stream().filter(c -> c.startsWith(prefix)).sorted().collect(Collectors.toList());
        return runChartAggregation(dayCols, groupId);
    }

    private List<Map<String, Object>> aggregateYearChart(String year, String groupId) {
        var cols = mongoTemplate.getCollectionNames();
        var prefix = CHART_COLLECTION + "_" + year + "_";
        var dayCols = cols.stream().filter(c -> c.startsWith(prefix)).sorted().collect(Collectors.toList());
        log.info("[Statistic] Chart year {}: {} day collections", year, dayCols.size());
        return runChartAggregation(dayCols, groupId);
    }

    private List<Map<String, Object>> mergeMonthRangeChart(String monthFrom, String monthTo, String groupId) {
        var dateStrs = resolveMonthRangeCollections(CHART_COLLECTION, monthFrom, monthTo);
        return runChartAggregation(dateStrs, groupId);
    }

    /**
     * Aggregate chart data using MongoDB: per day collection, use $unwind on topCountries/topIPs,
     * then $group by domain to sum requests, and format back to nested structure server-side.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> runChartAggregation(List<String> colNames, String groupId) {
        var zoneFilter = zoneCriteria(groupId);
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

        // For chart data, the in-memory merge is actually better than complex $unwind+$group
        // because the data volume is small (each day has ~10-50 domains, each with ~10 countries and ~50 IPs)
        // and reformatting after $unwind+$group back to nested structure is more complex.
        // However, we replaced the per-document iteration with per-aggregated-document iteration.
        for (String colName : colNames) {
            var pipeline = new ArrayList<AggregationOperation>();

            if (zoneFilter != null) {
                pipeline.add(Aggregation.match(zoneFilter));
            }

            // $unwind topCountries to get flat records for aggregation
            pipeline.add(Aggregation.unwind("topCountries", true));
            pipeline.add(Aggregation.unwind("topIPs", true));

            // $group by domain
            pipeline.add(Aggregation.group("domain")
                    .first("zoneId").as("zoneId")
                    .push(new Document("country", "$topCountries.country")
                            .append("requests", "$topCountries.requests")).as("countries")
                    .push(new Document("ip", "$topIPs.ip")
                            .append("country", "$topIPs.country")
                            .append("requests", "$topIPs.requests")).as("ips"));

            // Sort
            pipeline.add(Aggregation.sort(Sort.by(Sort.Direction.ASC, "_id")));

            var aggregation = Aggregation.newAggregation(pipeline);
            List<Map<String, Object>> results;
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rawResults = (List<Map<String, Object>>) (List<?>) mongoTemplate.aggregate(aggregation, colName, Map.class).getMappedResults();
                results = rawResults;
            } catch (Exception e) {
                log.warn("[Statistic] Chart aggregation failed for {}: {}", colName, e.getMessage());
                // Fallback to direct query + in-memory merge
                Query query = zoneFilter != null ? Query.query(zoneFilter) : new Query();
                List<Map<String, Object>> fallbackDocs = (List<Map<String, Object>>) (List<?>) mongoTemplate.find(query, Map.class, colName);
                for (var doc : fallbackDocs) {
                    mergeChartDoc(merged, doc);
                }
                continue;
            }

            for (var doc : results) {
                String domain = ((String) doc.getOrDefault("_id", "")).strip();
                if (domain.isBlank()) continue;

                merged.computeIfAbsent(domain, k -> {
                    var m = new HashMap<String, Object>();
                    m.put("domain", domain);
                    m.put("zoneId", doc.getOrDefault("zoneId", ""));
                    m.put("topCountries", new HashMap<String, Long>());
                    m.put("topIPs", new HashMap<String, Map<String, Object>>());
                    return m;
                });
                var acc = merged.get(domain);
                var countries = (Map<String, Long>) acc.get("topCountries");
                var ips = (Map<String, Map<String, Object>>) acc.get("topIPs");

                for (var c : (List<Map<String, Object>>) doc.getOrDefault("countries", Collections.emptyList())) {
                    String country = (String) c.get("country");
                    if (country != null && !country.isBlank()) {
                        countries.merge(country, ((Number) c.getOrDefault("requests", 0)).longValue(), Long::sum);
                    }
                }
                for (var item : (List<Map<String, Object>>) doc.getOrDefault("ips", Collections.emptyList())) {
                    String ip = (String) item.get("ip");
                    long requests = ((Number) item.getOrDefault("requests", 0)).longValue();
                    if (ip != null && !ip.isBlank()) {
                        ips.computeIfAbsent(ip, k -> {
                            var m = new HashMap<String, Object>();
                            m.put("ip", ip);
                            m.put("country", item.getOrDefault("country", ""));
                            m.put("requests", 0L);
                            return m;
                        });
                        var existing = ips.get(ip);
                        existing.put("requests", ((Number) existing.get("requests")).longValue() + requests);
                    }
                }
            }
        }

        return formatChartResult(merged);
    }

    // ======================== Legacy In-Memory Merge (kept as fallback) ========================

    @SuppressWarnings("unchecked")
    private void mergeChartDoc(Map<String, Map<String, Object>> merged, Map<String, Object> doc) {
        String domain = ((String) doc.getOrDefault("domain", "")).strip();
        if (domain.isBlank()) return;
        merged.computeIfAbsent(domain, k -> {
            var m = new HashMap<String, Object>();
            m.put("domain", domain);
            m.put("zoneId", doc.getOrDefault("zoneId", ""));
            m.put("topCountries", new HashMap<String, Long>());
            m.put("topIPs", new HashMap<String, Map<String, Object>>());
            return m;
        });
        var acc = merged.get(domain);
        var countries = (Map<String, Long>) acc.get("topCountries");
        var ips = (Map<String, Map<String, Object>>) acc.get("topIPs");

        for (var c : (List<Map<String, Object>>) doc.getOrDefault("topCountries", Collections.emptyList())) {
            String k = (String) c.get("country");
            if (k != null && !k.isBlank()) {
                countries.merge(k, ((Number) c.getOrDefault("requests", 0)).longValue(), Long::sum);
            }
        }
        for (var item : (List<Map<String, Object>>) doc.getOrDefault("topIPs", Collections.emptyList())) {
            String ip = (String) item.get("ip");
            long requests = ((Number) item.getOrDefault("requests", 0)).longValue();
            if (ip != null && !ip.isBlank()) {
                ips.computeIfAbsent(ip, k -> {
                    var m = new HashMap<String, Object>();
                    m.put("ip", ip);
                    m.put("country", item.getOrDefault("country", ""));
                    m.put("requests", 0L);
                    return m;
                });
                var existing = ips.get(ip);
                existing.put("requests", ((Number) existing.get("requests")).longValue() + requests);
            }
        }
    }

    private List<Map<String, Object>> formatChartResult(Map<String, Map<String, Object>> merged) {
        var result = new ArrayList<Map<String, Object>>();
        for (var entry : merged.entrySet()) {
            var v = entry.getValue();
            var countriesMap = (Map<String, Long>) v.get("topCountries");
            var ipsMap = (Map<String, Map<String, Object>>) v.get("topIPs");

            var countries = countriesMap.entrySet().stream()
                    .map(e -> Map.<String, Object>of("country", e.getKey(), "requests", e.getValue()))
                    .sorted((a, b) -> Long.compare(((Number) b.get("requests")).longValue(), ((Number) a.get("requests")).longValue()))
                    .limit(10)
                    .collect(Collectors.toList());

            var ips = ipsMap.values().stream()
                    .sorted((a, b) -> Long.compare(((Number) b.get("requests")).longValue(), ((Number) a.get("requests")).longValue()))
                    .limit(50)
                    .collect(Collectors.toList());

            var row = new HashMap<String, Object>();
            row.put("domain", v.get("domain"));
            row.put("zoneId", v.get("zoneId"));
            row.put("topCountries", countries);
            row.put("topIPs", ips);
            result.add(row);
        }
        result.sort(Comparator.comparing(o -> (String) o.get("domain")));
        return result;
    }

    // ======================== Utility: Collection Name Resolution ========================

    private List<String> resolveDateCollections(String base, String dateFrom, String dateTo) {
        var cols = mongoTemplate.getCollectionNames();
        var result = new ArrayList<String>();
        var from = LocalDate.parse(dateFrom);
        var to = LocalDate.parse(dateTo);
        var cursor = from;
        while (!cursor.isAfter(to)) {
            String colName = base + "_" + cursor.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
            if (cols.contains(colName)) {
                result.add(colName);
            }
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    private List<String> resolveMonthRangeCollections(String base, String monthFrom, String monthTo) {
        var cols = mongoTemplate.getCollectionNames();
        var result = new ArrayList<String>();

        var partsFrom = monthFrom.split("-");
        var partsTo = monthTo.split("-");
        int y1 = Integer.parseInt(partsFrom[0]), m1 = Integer.parseInt(partsFrom[1]);
        int y2 = Integer.parseInt(partsTo[0]), m2 = Integer.parseInt(partsTo[1]);

        while (y1 < y2 || (y1 == y2 && m1 <= m2)) {
            // Try pre-aggregated month collection first
            String mCol = base + "_" + String.format("%04d", y1) + "_" + String.format("%02d", m1);
            if (base.startsWith(TABLE_COLLECTION) && cols.contains(mCol)) {
                result.add(mCol);
            } else {
                // Fallback to day collections
                String prefix = mCol + "_";
                cols.stream().filter(c -> c.startsWith(prefix)).sorted().forEach(result::add);
            }
            m1++;
            if (m1 > 12) { m1 = 1; y1++; }
        }
        return result;
    }

}