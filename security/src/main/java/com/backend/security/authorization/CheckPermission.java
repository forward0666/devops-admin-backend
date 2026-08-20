package com.backend.security.authorization;

import java.lang.annotation.*;

/**
 * 阿里云 RAM 风格权限校验注解
 * 
 * 用法：
 * @CheckPermission(action = "project:create")
 * @CheckPermission(action = "user:delete", resource = "#userId")
 * @CheckPermission(action = "project:*")   // 允许所有 project 操作
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckPermission {
    
    /** 操作，如 "project:create", "user:delete" */
    String action();
    
    /** 资源表达式（可选），支持 SpEL，如 "#id", "#dto.projectId" */
    String resource() default "*";
}