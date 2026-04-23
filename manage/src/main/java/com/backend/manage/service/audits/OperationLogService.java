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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
            // 使用Java 21的模式匹配简化条件检查
            if (operationLog.getOperationId() == null) {
                operationLog.setOperationId(UUID.randomUUID().toString());
            }
            if (operationLog.getCreatedAt() == null) {
                operationLog.setCreatedAt(LocalDateTime.now());
            }
            mongoTemplate.save(operationLog);
        } catch (Exception e) {
            log.error("Failed to save operation log: {}", e.getMessage(), e);
        }
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
        return getOperationLogs(page, size, sortBy, sortDir, null);
    }

    public Page<OperationLogEntity> getOperationLogs(int page, int size, String sortBy, String sortDir, String category) {
        try {
            log.debug("Fetching operation logs: page={}, size={}, sortBy={}, sortDir={}, category={}", page, size, sortBy, sortDir, category);
            
            Pageable pageable = PageRequest.of(page, size,
                Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy));
            
            Query query = new Query();
            if (category != null && !category.isEmpty()) {
                query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("category").is(category));
            }
            long total = mongoTemplate.count(query, OperationLogEntity.class);
            
            List<OperationLogEntity> logs = mongoTemplate.find(
                query.skip((long) page * size).limit(size)
                    .with(Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy)),
                OperationLogEntity.class
            );
            
            log.info("✅ 成功查询操作日志: 总数={}, 当前页={}条", total, logs.size());
            return new PageImpl<>(logs, pageable, total);
        } catch (Exception e) {
            log.error("❌ 查询操作日志失败: {}", e.getMessage());
            // 降级：返回空结果而不抛出异常，确保不影响前端显示
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }
    }

    public List<OperationLogEntity> getRecentOperationLogs(int limit) {
        try {
            log.debug("Fetching recent operation logs: limit={}", limit);
            
            // 查询最近的 N 条日志
            Query query = new Query()
                .limit(limit)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
            
            List<OperationLogEntity> logs = mongoTemplate.find(query, OperationLogEntity.class);
            
            log.info("✅ 成功查询最近操作日志: {}条", logs.size());
            return logs;
        } catch (Exception e) {
            log.error("❌ 查询最近操作日志失败: {}", e.getMessage());
            // 降级：返回空结果
            return List.of();
        }
    }

    public void cleanupExpiredLogs(int i) {
    }
}
