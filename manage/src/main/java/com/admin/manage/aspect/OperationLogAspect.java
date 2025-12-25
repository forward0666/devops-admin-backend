package com.admin.manage.aspect;

import com.admin.manage.annotation.OperationLog;
import com.admin.manage.service.DepartmentService;
import com.admin.manage.service.OperationLogService;
import com.admin.manage.service.UserService;
import com.admin.manage.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {
    
    private final OperationLogService operationLogService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final DepartmentService departmentService;
    
    @Around("@annotation(com.admin.manage.annotation.OperationLog)")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        log.info("OperationLogAspect triggered for method: {}", joinPoint.getSignature().getName());
        
        // 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationLog operationLogAnnotation = method.getAnnotation(OperationLog.class);
        
        log.info("Operation annotation found: operationType={}, operationName={}", 
                operationLogAnnotation.operationType(), operationLogAnnotation.operationName());
        
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        
        // 获取用户信息
        Long userId = null;
        String username = null;
        if (request != null) {
            String token = extractToken(request);
            if (token != null && jwtUtil.validateToken(token)) {
                userId = jwtUtil.getUserIdFromToken(token);
                username = jwtUtil.getUsernameFromToken(token);
                log.info("Extracted user info: userId={}, username={}", userId, username);
            } else {
                log.warn("No valid JWT token found in request");
            }
        } else {
            log.warn("No request context found");
        }
        
        // 获取请求参数
        Object[] args = joinPoint.getArgs();
        Object requestBody = args.length > 0 ? args[0] : null;
        
        // 提取资源ID和目标名称（在操作执行前）
        String resourceId = extractResourceId(args, operationLogAnnotation.resourceIdIndex());
        String preOpTargetUsername = null;
        
        // 对于DELETE操作，必须在删除前获取目标名称
        if ("DELETE".equals(operationLogAnnotation.operationType())) {
            log.info("DELETE operation detected. ResourceType: {}, ResourceId: {}", 
                    operationLogAnnotation.resourceType(), resourceId);
            
            // 直接测试用户服务
            if ("USER".equals(operationLogAnnotation.resourceType()) && resourceId != null) {
                try {
                    Long targetUserId = Long.parseLong(resourceId);
                    log.info("Direct test: Calling userService.getUserByIdIncludeInactive({}) before DELETE", targetUserId);
                    var testUser = userService.getUserByIdIncludeInactive(targetUserId);
                    if (testUser != null) {
                        log.info("Direct test: Found user {} with username: {}", targetUserId, testUser.getUsername());
                    } else {
                        log.warn("Direct test: userService.getUserByIdIncludeInactive({}) returned null", targetUserId);
                    }
                } catch (Exception e) {
                    log.error("Direct test: Exception calling userService.getUserByIdIncludeInactive({}): {}", resourceId, e.getMessage(), e);
                }
            }
            
            preOpTargetUsername = extractTargetUsername(args, operationLogAnnotation.resourceType(), resourceId, null);
            log.info("Pre-operation target username for DELETE: {}", preOpTargetUsername);
            
            if (preOpTargetUsername == null) {
                log.warn("Failed to extract target username before DELETE operation. This will result in null details field.");
                log.warn("Args: {}, ResourceType: {}, ResourceId: {}", args, operationLogAnnotation.resourceType(), resourceId);
            }
        }
        
        Object result = null;
        boolean success = true;
        String errorMessage = null;
        
        try {
            // 执行目标方法
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            // 记录操作日志
            try {
                long executionTime = System.currentTimeMillis() - startTime;
                
                log.info("Attempting to log operation: success={}, executionTime={}ms", success, executionTime);
                
                if (request != null) {
                    // 对于DELETE操作使用预先获取的名称，其他操作可以从结果中获取
                    String finalTargetUsername = preOpTargetUsername != null ? preOpTargetUsername : 
                                               extractTargetUsername(args, operationLogAnnotation.resourceType(), resourceId, result);
                    
                    log.info("Logging operation with resourceId: {}, finalTargetUsername: {}", resourceId, finalTargetUsername);
                    
                    operationLogService.logUserOperation(
                            userId,
                            username,
                            operationLogAnnotation.operationType(),
                            operationLogAnnotation.operationName(),
                            operationLogAnnotation.resourceType(),
                            resourceId,
                            request,
                            requestBody,
                            result,
                            success,
                            errorMessage,
                            finalTargetUsername
                    );
                    
                    log.info("Operation log submitted successfully");
                } else {
                    log.warn("Cannot log operation: no request context");
                }
            } catch (Exception e) {
                log.error("Failed to log operation: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 从请求中提取JWT token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    /**
     * 提取资源ID
     */
    private String extractResourceId(Object[] args, int resourceIdIndex) {
        if (resourceIdIndex >= 0 && resourceIdIndex < args.length) {
            Object resourceId = args[resourceIdIndex];
            return resourceId != null ? resourceId.toString() : null;
        }
        return null;
    }
    
    /**
     * 提取目标资源名称（用于用户和部门操作）
     */
    private String extractTargetUsername(Object[] args, String resourceType, String resourceId, Object result) {
        log.info("Extracting target name for resourceType: {}, resourceId: {}", resourceType, resourceId);
        
        // 对于部门操作，优先从响应体中提取名称（因为PUT请求的requestBody通常只包含ID）
        if ("DEPARTMENT".equals(resourceType) && result != null) {
            try {
                String resultJson = objectMapper.writeValueAsString(result);
                log.info("Checking result JSON for department name: {}", resultJson);
                
                var resultNode = objectMapper.readTree(resultJson);
                
                // 检查响应体结构：可能是 {body: {data: {name: "..."}}} 或直接包含name字段
                if (resultNode.has("body") && resultNode.get("body").has("data")) {
                    var dataNode = resultNode.get("body").get("data");
                    if (dataNode.has("name")) {
                        String departmentName = dataNode.get("name").asText();
                        log.info("Extracted department name from response body: {}", departmentName);
                        return departmentName;
                    }
                } else if (resultNode.has("data") && resultNode.get("data").has("name")) {
                    String departmentName = resultNode.get("data").get("name").asText();
                    log.info("Extracted department name from response data: {}", departmentName);
                    return departmentName;
                } else if (resultNode.has("name")) {
                    String departmentName = resultNode.get("name").asText();
                    log.info("Extracted department name from response: {}", departmentName);
                    return departmentName;
                }
            } catch (Exception e) {
                log.warn("Failed to extract department name from response body: {}", e.getMessage());
            }
        }
        
        // 首先尝试从请求体中提取名称
        for (Object arg : args) {
            if (arg != null) {
                try {
                    String json = objectMapper.writeValueAsString(arg);
                    log.info("Checking argument JSON for {}: {}", resourceType, json);
                    
                    var node = objectMapper.readTree(json);
                    
                    // 根据资源类型提取不同的字段
                    if ("USER".equals(resourceType) && node.has("username")) {
                        String username = node.get("username").asText();
                        log.info("Extracted username from request body: {}", username);
                        return username;
                    } else if ("DEPARTMENT".equals(resourceType)) {
                        // 尝试多个可能的字段名
                        if (node.has("name")) {
                            String departmentName = node.get("name").asText();
                            log.info("Extracted department name from request body (name field): {}", departmentName);
                            return departmentName;
                        } else if (node.has("departmentName")) {
                            String departmentName = node.get("departmentName").asText();
                            log.info("Extracted department name from request body (departmentName field): {}", departmentName);
                            return departmentName;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract name from argument: {}", e.getMessage());
                }
            }
        }
        
        // 如果从请求体和响应体中都无法获取名称，尝试从数据库查询
        if (resourceId != null && !resourceId.trim().isEmpty()) {
            try {
                log.info("Attempting to fetch name from database for resourceType: {}, resourceId: {}", resourceType, resourceId);
                Long id = Long.parseLong(resourceId.trim());
                
                if ("USER".equals(resourceType)) {
                    log.info("Attempting to fetch user from database with ID: {}", id);
                    try {
                        var user = userService.getUserByIdIncludeInactive(id);
                        if (user != null) {
                            String username = user.getUsername();
                            log.info("Successfully fetched username from database: {} for userId: {}", username, id);
                            return username;
                        } else {
                            log.warn("User not found in database for userId: {} - getUserByIdIncludeInactive returned null", id);
                        }
                    } catch (Exception e) {
                        log.error("Exception occurred while fetching user with ID {}: {}", id, e.getMessage(), e);
                    }
                } else if ("DEPARTMENT".equals(resourceType)) {
                    try {
                        log.info("Calling departmentService.getDepartmentById({}) for department name lookup", id);
                        var department = departmentService.getDepartmentById(id);
                        if (department != null) {
                            String departmentName = department.getName();
                            log.info("Successfully fetched department name from database: {} for departmentId: {}", departmentName, id);
                            return departmentName;
                        } else {
                            log.warn("Department not found in database for departmentId: {}", id);
                        }
                    } catch (Exception e) {
                        log.error("Exception occurred while fetching department name for departmentId {}: {}", id, e.getMessage(), e);
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid resourceId format: {}", resourceId);
            } catch (Exception e) {
                log.error("Failed to fetch name from database for {} {}: {}", resourceType, resourceId, e.getMessage(), e);
            }
        } else {
            log.warn("ResourceId is null or empty, cannot fetch name");
        }
        
        return null;
    }
}