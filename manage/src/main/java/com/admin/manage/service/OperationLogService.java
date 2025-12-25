package com.admin.manage.service;

import com.admin.manage.model.OperationLog;
import com.admin.manage.repository.OperationLogRepository;
import com.admin.manage.util.JsonUtil;
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
    public void logOperation(OperationLog operationLog) {
        try {
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
            HttpServletRequest request,
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

            OperationLog operationLog = OperationLog.builder()
                    .operationId(UUID.randomUUID().toString())
                    .userId(userId)
                    .username(username)
                    .operationType(operationType)
                    .operationName(operationName)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .method(request.getMethod())
                    .url(request.getRequestURI())
                    .ipAddress(getClientIpAddress(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .requestBody(JsonUtil.toJson(requestBody))
                    .responseBody(JsonUtil.toJson(responseBody))
                    .success(success)
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .module("MANAGE")
                    .details(details)
                    .build();

            logOperation(operationLog);
        } catch (JsonProcessingException e) {
            log.error("Failed to log user operation: {}", e.getMessage(), e);
        }
    }

    public Page<OperationLog> getOperationLogs(int page, int size, String sortBy, String sortDir) {
        return operationLogRepository.findOperationLogs(page, size, sortBy, sortDir);
    }

    public List<OperationLog> getRecentOperationLogs(int limit) {
        return operationLogRepository.findTop5ByOrderByCreatedAtDesc();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    public void cleanupExpiredLogs(int i) {
    }
}
