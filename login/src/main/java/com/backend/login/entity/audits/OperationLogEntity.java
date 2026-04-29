package com.backend.login.entity.audits;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "operation_logs_190001")
public class OperationLogEntity {

    @Id
    private String id;

    private String operationId;
    private Long userId;
    private String username;
    private String operationType;
    private String operationName;
    private String resourceType;
    private String resourceId;
    private String method;
    private String url;
    private String ipAddress;
    private String userAgent;
    private Map<String, Object> requestBody;
    private Map<String, Object> responseBody;
    private String status;
    private String errorMessage;
    private String category;
    private LocalDateTime createdAt;
}
