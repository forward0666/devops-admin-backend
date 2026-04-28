package com.backend.manage.service.audits;

import com.backend.manage.entity.audits.OperationLogEntity;
import com.backend.manage.mapper.mongo.OperationLogMapper;
import com.backend.manage.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
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

    private final OperationLogMapper operationLogMapper;
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
            String collectionName = operationLogMapper.getCollectionName(operationLog.getCreatedAt());
            operationLogMapper.insert(operationLog, collectionName);
        } catch (Exception e) {
            log.error("Failed to save operation log: {}", e.getMessage(), e);
        }
    }

    @Async
    public void logUserOperation(
            Long userId, String username, String operationType, String operationName,
            String resourceType, String resourceId, String method, String url,
            String ipAddress, String userAgent, Object requestBody, Object responseBody,
            boolean success, String errorMessage, String targetUsername) {
        logUserOperation(userId, username, operationType, operationName, resourceType, resourceId,
            method, url, ipAddress, userAgent, requestBody, responseBody, success, errorMessage,
            targetUsername, "OPERATION");
    }

    public void logUserOperation(
            Long userId, String username, String operationType, String operationName,
            String resourceType, String resourceId, String method, String url,
            String ipAddress, String userAgent, Object requestBody, Object responseBody,
            boolean success, String errorMessage, String targetUsername, String category) {
        try {
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
        } catch (Exception e) {
            log.error("Failed to log user operation: {}", e.getMessage(), e);
        }
    }

    public Page<OperationLogEntity> getOperationLogs(int page, int size, String sortBy, String sortDir) {
        return getOperationLogs(page, size, sortBy, sortDir, null, null, null);
    }

    public Page<OperationLogEntity> getOperationLogs(int page, int size, String sortBy, String sortDir,
            String category, String startDate, String endDate) {
        try {
            log.debug("Fetching operation logs: page={}, size={}, sortBy={}, sortDir={}, category={}, startDate={}, endDate={}",
                page, size, sortBy, sortDir, category, startDate, endDate);

            Pageable pageable = PageRequest.of(page, size);
            var result = operationLogMapper.findByCriteriaWithTotal(
                category, startDate, endDate, page, size, sortBy, sortDir);

            log.info("Fetched operation logs: total={}, returned={}", result.total(), result.logs().size());
            return new PageImpl<>(result.logs(), pageable, result.total());
        } catch (Exception e) {
            log.error("Failed to query operation logs: {}", e.getMessage());
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }
    }

    public List<OperationLogEntity> getRecentOperationLogs(int limit) {
        try {
            log.debug("Fetching recent operation logs: limit={}", limit);
            List<OperationLogEntity> logs = operationLogMapper.findRecent(limit);
            log.info("Fetched recent operation logs: {}", logs.size());
            return logs;
        } catch (Exception e) {
            log.error("Failed to query recent operation logs: {}", e.getMessage());
            return List.of();
        }
    }

    public void cleanupExpiredLogs(int days) {
        // TODO: implement cleanup
    }
}
