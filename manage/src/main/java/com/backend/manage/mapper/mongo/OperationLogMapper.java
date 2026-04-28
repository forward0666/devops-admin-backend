package com.backend.manage.mapper.mongo;

import com.backend.manage.entity.audits.OperationLogEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OperationLogMapper {

    private final MongoTemplate mongoTemplate;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

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

    // ==================== Insert ====================

    public void insert(OperationLogEntity entity, String collectionName) {
        mongoTemplate.save(entity, collectionName);
    }

    // ==================== Select ====================

    public List<OperationLogEntity> findByCriteria(String category, String startDate, String endDate,
                                                    int page, int size, String sortBy, String sortDir) {
        Query query = buildCriteriaQuery(category, startDate, endDate);
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        query.with(sort);

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

        // Global sort across collections
        boolean isAsc = sortDir.equalsIgnoreCase("asc");
        allLogs.sort(isAsc
            ? java.util.Comparator.comparing(OperationLogEntity::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
            : java.util.Comparator.comparing(OperationLogEntity::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));

        // Memory pagination
        long skip = (long) page * size;
        return allLogs.stream().skip(skip).limit(size).toList();
    }

    public long countByCriteria(String category, String startDate, String endDate) {
        Query query = buildCriteriaQuery(category, startDate, endDate);

        LocalDateTime queryStart = LocalDateTime.now().minusMonths(6);
        LocalDateTime queryEnd = LocalDateTime.now();
        if (startDate != null && !startDate.isEmpty()) {
            queryStart = LocalDate.parse(startDate).atStartOfDay();
        }
        if (endDate != null && !endDate.isEmpty()) {
            queryEnd = LocalDate.parse(endDate).atStartOfDay().plusDays(1);
        }

        List<String> collections = getQueryableCollections(queryStart, queryEnd);
        long total = 0;
        for (String col : collections) {
            try {
                total += mongoTemplate.count(query, OperationLogEntity.class, col);
            } catch (Exception e) {
                log.debug("Collection {} not found, skipping", col);
            }
        }
        return total;
    }

    public List<OperationLogEntity> findRecent(int limit) {
        List<String> collections = getQueryableCollections(
            LocalDateTime.now().minusMonths(3), LocalDateTime.now());

        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt"));
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
        Query query = new Query();

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
