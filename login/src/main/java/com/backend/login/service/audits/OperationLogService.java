package com.backend.login.service.audits;

import com.backend.login.entity.audits.OperationLogEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final MongoTemplate mongoTemplate;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    @Async
    public void logUserOperation(
            Long userId, String username, String operationType, String operationName,
            String resourceType, String resourceId, String method, String url,
            String ipAddress, String userAgent, Object requestBody, Object responseBody,
            boolean success, String errorMessage, String category) {
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

            String collectionName = "operation_logs_" + operationLog.getCreatedAt().format(MONTH_FORMATTER);
            mongoTemplate.save(operationLog, collectionName);
        } catch (Exception e) {
            log.error("Failed to save login operation log: {}", e.getMessage());
        }
    }
}
