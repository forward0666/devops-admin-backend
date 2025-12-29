package com.backend.manage.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 用于标记需要记录操作日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    
    /**
     * 操作类型
     */
    String operationType();
    
    /**
     * 操作名称
     */
    String operationName();
    
    /**
     * 资源类型
     */
    String resourceType();
    
    /**
     * 资源ID在方法参数中的索引位置，-1表示不记录资源ID
     */
    int resourceIdIndex() default -1;
    
    /**
     * 操作描述
     */
    String description() default "";
    
    /**
     * 是否记录请求参数
     */
    boolean logRequest() default true;
    
    /**
     * 是否记录响应结果
     */
    boolean logResponse() default true;
}
