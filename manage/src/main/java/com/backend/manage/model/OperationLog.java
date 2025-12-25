package com.backend.manage.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "operation_logs")
public class OperationLog {
    
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
    
    @Field("ip_address")
    private String ipAddress;
    
    @Field("user_agent")
    private String userAgent;
    
    @Field("request_params")
    private Map<String, Object> requestParams;
    
    @Field("request_body")
    private String requestBody;
    
    @Field("response_status")
    private Integer responseStatus;
    
    @Field("response_body")
    private String responseBody;
    
    @Field("execution_time")
    private Long executionTime;
    
    @Field("success")
    private Boolean success;
    
    @Field("error_message")
    private String errorMessage;
    
    @Field("created_at")
    private LocalDateTime createdAt;
    
    @Field("module")
    private String module;
    
    @Field("description")
    private String description;
    
    @Field("before_data")
    private String beforeData;
    
    @Field("after_data")
    private String afterData;
    
    @Field("details")
    private String details;

    // 操作类型枚举
    public enum OperationType {
        CREATE("CREATE", "创建"),
        UPDATE("UPDATE", "更新"),
        DELETE("DELETE", "删除"),
        QUERY("QUERY", "查询"),
        LOGIN("LOGIN", "登录"),
        LOGOUT("LOGOUT", "登出"),
        EXPORT("EXPORT", "导出"),
        IMPORT("IMPORT", "导入"),
        UPLOAD("UPLOAD", "上传"),
        DOWNLOAD("DOWNLOAD", "下载"),
        CONFIG("CONFIG", "配置"),
        SECURITY("SECURITY", "安全操作");
        
        private final String code;
        private final String description;
        
        OperationType(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // 资源类型枚举
    public enum ResourceType {
        USER("USER", "用户"),
        DEPARTMENT("DEPARTMENT", "部门"),
        ROLE("ROLE", "角色"),
        PERMISSION("PERMISSION", "权限"),
        SYSTEM_CONFIG("SYSTEM_CONFIG", "系统配置"),
        SECURITY_CONFIG("SECURITY_CONFIG", "安全配置"),
        FILE("FILE", "文件"),
        LOG("LOG", "日志");
        
        private final String code;
        private final String description;
        
        ResourceType(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
}