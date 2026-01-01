package com.backend.manage.service;

import com.backend.manage.entity.OperationLogEntity;
import com.backend.manage.repository.OperationLogRepository;
import com.backend.manage.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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

    private final OperationLogRepository operationLogRepository;
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
            operationLogRepository.save(operationLog);
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
                    .createdAt(LocalDateTime.now())
                    .build();

            logOperation(operationLog);
        } catch (JsonProcessingException e) {
            log.error("Failed to log user operation: {}", e.getMessage(), e);
        }
    }

    public Page<OperationLogEntity> getOperationLogs(int page, int size, String sortBy, String sortDir) {
        return operationLogRepository.findOperationLogs(page, size, sortBy, sortDir);
    }

    public List<OperationLogEntity> getRecentOperationLogs(int limit) {
        return operationLogRepository.findTop5ByOrderByCreatedAtDesc();
    }

    public void cleanupExpiredLogs(int i) {
    }
}
