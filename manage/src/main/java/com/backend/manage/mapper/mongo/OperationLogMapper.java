package com.backend.manage.mapper.mongo;

import com.backend.manage.entity.audits.OperationLogEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OperationLogMapper {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String QUERY_FILE = "mongo/OperationLogRepository.json";

    private JsonNode queryDefinitions;

    @PostConstruct
    public void init() {
        try {
            InputStream is = new ClassPathResource(QUERY_FILE).getInputStream();
            queryDefinitions = objectMapper.readTree(is);
            log.info("Loaded MongoDB query definitions from {}", QUERY_FILE);
        } catch (Exception e) {
            log.error("Failed to load query definitions from {}: {}", QUERY_FILE, e.getMessage());
            queryDefinitions = objectMapper.createObjectNode();
        }
    }

    // ==================== Collection Name ====================

    public String getCollectionName(LocalDateTime dateTime) {
        return "operation_logs_" + dateTime.format(MONTH_FORMATTER);
    }

    public List<String> getCollectionNamesForRange(LocalDateTime start, LocalDateTime end) {
        List<String> names = new ArrayList<>();
        java.time.YearMonth current = java.time.YearMonth.from(start);
        java.time.YearMonth endMonth = java.time.YearMonth.from(end);
        while (!current.isAfter(endMonth)) {
            names.add("operation_logs_" + current.format(MONTH_FORMATTER));
            current = current.plusMonths(1);
        }
        return names;
    }

    public List<String> getQueryableCollections(LocalDateTime start, LocalDateTime end) {
        List<String> collections = getCollectionNamesForRange(start, end);
        Collections.reverse(collections);
        if (!collections.contains("operation_logs")) {
            collections.add("operation_logs");
        }
        return collections;
    }

    // ==================== Query Template ====================

    private Query buildQueryFromTemplate(String templateName) {
        Query query = new Query();
        JsonNode def = queryDefinitions.get(templateName);
        if (def == null) return query;

        // Apply sort from template
        JsonNode sortNode = def.get("sort");
        if (sortNode != null && sortNode.isObject()) {
            List<Sort.Order> orders = new ArrayList<>();
            sortNode.fields().forEachRemaining(entry -> {
                int direction = entry.getValue().asInt(-1);
                orders.add(new Sort.Order(direction == 1 ? Sort.Direction.ASC : Sort.Direction.DESC, entry.getKey()));
            });
            if (!orders.isEmpty()) {
                query.with(Sort.by(orders));
            }
        }

        return query;
    }

    // ==================== Insert ====================

    public void insert(OperationLogEntity entity, String collectionName) {
        mongoTemplate.save(entity, collectionName);
    }

    // ==================== Select ====================

    public List<OperationLogEntity> findByCriteria(String category, String startDate, String endDate,
                                                    int page, int size, String sortBy, String sortDir) {
        return findByCriteriaWithTotal(category, startDate, endDate, page, size, sortBy, sortDir).logs();
    }

    public long countByCriteria(String category, String startDate, String endDate) {
        return findByCriteriaWithTotal(category, startDate, endDate, 0, Integer.MAX_VALUE, "createdAt", "desc").total();
    }

    public record QueryResult(List<OperationLogEntity> logs, long total) {}

    public QueryResult findByCriteriaWithTotal(String category, String startDate, String endDate,
                                                int page, int size, String sortBy, String sortDir) {
        Query query = buildCriteriaQuery(category, startDate, endDate);

        // Use sort from JSON template, allow override by params
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort.Direction dir = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
            query.with(Sort.by(dir, sortBy));
        } else {
            query = buildQueryFromTemplate("findOperationLogs");
            buildCriteriaQuery(category, startDate, endDate).getCriteriaObject()
                .forEach(query::addCriteria);
        }

        LocalDateTime queryStart = LocalDateTime.now().minusMonths(6);
        LocalDateTime queryEnd = LocalDateTime.now();
        if (startDate != null && !startDate.isEmpty()) {
            queryStart = LocalDate.parse(startDate).atStartOfDay();
        }
        if (endDate != null && !endDate.isEmpty()) {
            queryEnd = LocalDate.parse(endDate).atStartOfDay().plusDays(1);
        }

        List<String> collections = getQueryableCollections(queryStart, queryEnd);
        List<OperationLogEntity> allLogs = new ArrayList<>();
        for (String col : collections) {
            try {
                List<OperationLogEntity> logs = mongoTemplate.find(query, OperationLogEntity.class, col);
                allLogs.addAll(logs);
            } catch (Exception e) {
                log.debug("Collection {} not found, skipping", col);
            }
        }

        long total = allLogs.size();

        // Global sort across collections
        boolean isAsc = sortDir != null && sortDir.equalsIgnoreCase("asc");
        allLogs.sort(isAsc
            ? Comparator.comparing(OperationLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            : Comparator.comparing(OperationLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        long skip = (long) page * size;
        List<OperationLogEntity> paged = allLogs.stream().skip(skip).limit(size).toList();
        return new QueryResult(paged, total);
    }

    public List<OperationLogEntity> findRecent(int limit) {
        Query query = buildQueryFromTemplate("findRecentLogs");
        List<String> collections = getQueryableCollections(
            LocalDateTime.now().minusMonths(3), LocalDateTime.now());

        List<OperationLogEntity> allLogs = new ArrayList<>();
        for (String col : collections) {
            if (allLogs.size() >= limit) break;
            Query colQuery = query.limit(limit - allLogs.size());
            List<OperationLogEntity> logs = mongoTemplate.find(colQuery, OperationLogEntity.class, col);
            allLogs.addAll(logs);
        }
        return allLogs.stream().limit(limit).toList();
    }

    // ==================== Private Helpers ====================

    private Query buildCriteriaQuery(String category, String startDate, String endDate) {
        Query query = buildQueryFromTemplate("findOperationLogs");

        if (category != null && !category.isEmpty()) {
            query.addCriteria(Criteria.where("category").is(category));
        }

        if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
            Criteria dateCriteria = Criteria.where("createdAt");
            if (startDate != null && !startDate.isEmpty()) {
                dateCriteria.gte(LocalDate.parse(startDate).atStartOfDay());
            }
            if (endDate != null && !endDate.isEmpty()) {
                dateCriteria.lt(LocalDate.parse(endDate).atStartOfDay().plusDays(1));
            }
            query.addCriteria(dateCriteria);
        }

        return query;
    }
}
