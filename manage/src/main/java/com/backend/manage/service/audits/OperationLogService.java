package com.backend.manage.service.audits;

import com.backend.manage.entity.audits.OperationLogEntity;
import com.backend.manage.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    @Async
    public void logOperation(OperationLogEntity operationLog) {
        try {
            if (operationLog.getOperationId() == null) {
                operationLog.setOperationId(UUID.randomUUID().toString());
            }
            if (operationLog.getCreatedAt() == null) {
                operationLog.setCreatedAt(LocalDateTime.now());
            }
            String collectionName = getCollectionName(operationLog.getCreatedAt());
            mongoTemplate.save(operationLog, collectionName);
        } catch (Exception e) {
            log.error("Failed to save operation log: {}", e.getMessage(), e);
        }
    }

    private String getCollectionName(LocalDateTime dateTime) {
        return "operation_logs_" + dateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /**
     * Query across monthly collections for a date range
     */
    private List<String> getCollectionNamesForRange(LocalDateTime start, LocalDateTime end) {
        List<String> names = new java.util.ArrayList<>();
        java.time.YearMonth current = java.time.YearMonth.from(start);
        java.time.YearMonth endMonth = java.time.YearMonth.from(end);
        while (!current.isAfter(endMonth)) {
            names.add("operation_logs_" + current.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")));
            current = current.plusMonths(1);
        }
        return names;
    }

    @Async
    public void logUserOperation(
            Long userId,
            String username,
            String operationType,
            String operationName,
            String resourceType,
            String resourceId,
            String method,
            String url,
            String ipAddress,
            String userAgent,
            Object requestBody,
            Object responseBody,
            boolean success,
            String errorMessage,
            String targetUsername) {
        logUserOperation(userId, username, operationType, operationName, resourceType, resourceId, method, url, ipAddress, userAgent, requestBody, responseBody, success, errorMessage, targetUsername, "OPERATION");
    }

    public void logUserOperation(
            Long userId,
            String username,
            String operationType,
            String operationName,
            String resourceType,
            String resourceId,
            String method,
            String url,
            String ipAddress,
            String userAgent,
            Object requestBody,
            Object responseBody,
            boolean success,
            String errorMessage,
            String targetUsername,
            String category) {
        try {
            String details = null;
            if (targetUsername != null) {
                Map<String, Object> detailsMap = Map.of(
                        "USER".equals(resourceType) ? "targetUsername" : "targetDepartmentName", targetUsername
                );
                details = objectMapper.writeValueAsString(detailsMap);
            }

            OperationLogEntity operationLog = OperationLogEntity.builder()
                    .operationId(UUID.randomUUID().toString())
                    .userId(userId)
                    .username(username)
                    .operationType(operationType)
                    .operationName(operationName)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .method(method)
                    .url(url)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .requestBody(requestBody instanceof Map ? (Map<String, Object>) requestBody : null)
                    .status(success ? "success" : "failed")
                    .errorMessage(errorMessage)
                    .category(category)
                    .createdAt(LocalDateTime.now())
                    .build();

            logOperation(operationLog);
        } catch (JsonProcessingException e) {
            log.error("Failed to log user operation: {}", e.getMessage(), e);
        }
    }

    public Page<OperationLogEntity> getOperationLogs(int page, int size, String sortBy, String sortDir) {
        return getOperationLogs(page, size, sortBy, sortDir, null, null, null);
    }

    public Page<OperationLogEntity> getOperationLogs(int page, int size, String sortBy, String sortDir, String category,
            String startDate, String endDate) {
        try {
            log.debug("Fetching operation logs: page={}, size={}, sortBy={}, sortDir={}, category={}, startDate={}, endDate={}",
                page, size, sortBy, sortDir, category, startDate, endDate);
            
            Pageable pageable = PageRequest.of(page, size,
                Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy));
            
            Query query = new Query();
            if (category != null && !category.isEmpty()) {
                query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("category").is(category));
            }
            if (startDate != null && !startDate.isEmpty()) {
                LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
                query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("createdAt").gte(start));
            }
            if (endDate != null && !endDate.isEmpty()) {
                LocalDateTime end = LocalDate.parse(endDate).atStartOfDay().plusDays(1);
                query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("createdAt").lt(end));
            }

            // Query recent 6 months collections + legacy collection
            // Determine date range for collection selection
            LocalDateTime queryStart = LocalDateTime.now().minusMonths(6);
            LocalDateTime queryEnd = LocalDateTime.now();
            if (startDate != null && !startDate.isEmpty()) {
                queryStart = LocalDate.parse(startDate).atStartOfDay();
            }
            if (endDate != null && !endDate.isEmpty()) {
                queryEnd = LocalDate.parse(endDate).atStartOfDay().plusDays(1);
            }
            List<String> collections = getCollectionNamesForRange(queryStart, queryEnd);
            Collections.reverse(collections);
            if (!collections.contains("operation_logs")) {
                collections.add("operation_logs");
            }

            Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
            Query queryWithSort = query.with(sort);

            // Collect all matching logs from all collections
            List<OperationLogEntity> allLogs = new java.util.ArrayList<>();
            for (String col : collections) {
                try {
                    List<OperationLogEntity> logs = mongoTemplate.find(queryWithSort, OperationLogEntity.class, col);
                    allLogs.addAll(logs);
                } catch (Exception e) {
                    log.debug("Collection {} not found, skipping", col);
                }
            }

            // Global sort across collections
            Comparator<OperationLogEntity> comparator = sortDir.equalsIgnoreCase("asc")
                ? Comparator.comparing(OperationLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                : Comparator.comparing(OperationLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            allLogs.sort(comparator);

            long total = allLogs.size();
            long skip = (long) page * size;
            List<OperationLogEntity> paged = allLogs.stream()
                    .skip(skip)
                    .limit(size)
                    .toList();

            log.info("Fetched operation logs: total={}, returned={}", total, paged.size());
            return new PageImpl<>(paged, pageable, total);
        } catch (Exception e) {
            log.error("Failed to query operation logs: {}", e.getMessage());
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }
    }

    public List<OperationLogEntity> getRecentOperationLogs(int limit) {
        try {
            log.debug("Fetching recent operation logs: limit={}", limit);
            
            List<String> collections = getCollectionNamesForRange(
                LocalDateTime.now().minusMonths(3), LocalDateTime.now());
            Collections.reverse(collections);
            if (!collections.contains("operation_logs")) {
                collections.add("operation_logs");
            }

            List<OperationLogEntity> allLogs = new java.util.ArrayList<>();
            Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt"));
            for (String col : collections) {
                if (allLogs.size() >= limit) break;
                Query colQuery = query.limit(limit - allLogs.size());
                List<OperationLogEntity> logs = mongoTemplate.find(colQuery, OperationLogEntity.class, col);
                allLogs.addAll(logs);
            }

            log.info("Fetched recent operation logs: {}", allLogs.size());
            return allLogs.stream().limit(limit).toList();
        } catch (Exception e) {
            log.error("❌ 查询最近操作日志失败: {}", e.getMessage());
            // 降级：返回空结果
            return List.of();
        }
    }

    public void cleanupExpiredLogs(int i) {
    }
}
