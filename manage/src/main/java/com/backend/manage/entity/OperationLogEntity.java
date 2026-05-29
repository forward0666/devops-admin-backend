package com.backend.manage.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 操作日志实体类
 * 用于记录用户的操作行为，使用 MongoDB 存储
 * 使用 Java 21 风格
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "operation_logs")
public class OperationLogEntity {

    @Id
    private String id;

    @Field("operation_id")
    private String operationId;

    @Field("user_id")
    private Long userId;

    @Field("username")
    private String username;

    @Field("operation_type")
    private String operationType;

    @Field("operation_name")
    private String operationName;

    @Field("resource_type")
    private String resourceType;

    @Field("resource_id")
    private String resourceId;

    @Field("method")
    private String method;

    @Field("url")
    private String url;

    @Field("request_body")
    private Map<String, Object> requestBody;

    @Field("ip_address")
    private String ipAddress;

    @Field("user_agent")
    private String userAgent;

    @Field("status")
    private String status;

    @Field("response_code")
    private Integer responseCode;

    @Field("error_message")
    private String errorMessage;

    @Field("execution_time")
    private Long executionTime;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("timestamp")
    private LocalDateTime timestamp;

    @Field("category")
    private String category;
}
