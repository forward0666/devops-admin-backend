package com.backend.manage.aspect;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.service.DepartmentService;
import com.backend.manage.service.OperationLogService;
import com.backend.manage.service.UserService;
import com.backend.manage.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
    
    @Around("@annotation(com.backend.manage.annotation.OperationLog)")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationLog annotation = method.getAnnotation(OperationLog.class);
        
        log.info("OperationLogAspect triggered for method: {}, operationType={}, operationName={}", 
                joinPoint.getSignature().getName(), annotation.operationType(), annotation.operationName());
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        if (request == null) {
            log.warn("No request context available, skipping operation logging");
            return joinPoint.proceed();
        }
        
        Long userId = null;
        String username = null;
        UserInfo userInfo = extractUserInfo(request);
        if (userInfo != null) {
            userId = userInfo.userId();
            username = userInfo.username();
        }
        
        Object[] args = joinPoint.getArgs();
        Object requestBody = args.length > 0 ? args[0] : null;
        String resourceId = extractResourceId(args, annotation.resourceIdIndex());
        
        String preOpTargetUsername = null;
        if ("DELETE".equals(annotation.operationType())) {
            log.info("DELETE operation detected. ResourceType: {}, ResourceId: {}", 
                    annotation.resourceType(), resourceId);
            preOpTargetUsername = resolveTargetNameBeforeDelete(args, annotation.resourceType(), resourceId);
        }
        
        Object result = null;
        boolean success = true;
        String errorMessage = null;
        
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            persistOperationLog(request, annotation, userId, username, resourceId, 
                    requestBody, result, preOpTargetUsername, success, errorMessage, startTime);
        }
    }
    
    private record UserInfo(Long userId, String username) {}
    
    private UserInfo extractUserInfo(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null && jwtUtil.validateToken(token)) {
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            log.info("Extracted user info: userId={}, username={}", userId, username);
            return new UserInfo(userId, username);
        }
        log.warn("No valid JWT token found in request");
        return null;
    }
    
    private String resolveTargetNameBeforeDelete(Object[] args, String resourceType, String resourceId) {
        // For USER delete, fetch from DB before deletion
        if ("USER".equals(resourceType) && resourceId != null) {
            try {
                Long targetUserId = Long.parseLong(resourceId);
                var user = userService.getUserByIdIncludeInactive(targetUserId);
                if (user != null) {
                    log.info("Found user {} with username: {}", targetUserId, user.getUsername());
                    return user.getUsername();
                }
                log.warn("User not found for userId: {}", targetUserId);
            } catch (Exception e) {
                log.error("Failed to fetch user before DELETE, resourceId={}: {}", resourceId, e.getMessage(), e);
            }
        }
        
        String targetName = extractTargetUsername(args, resourceType, resourceId, null);
        if (targetName == null) {
            log.warn("Failed to extract target username before DELETE. Args: {}, ResourceType: {}, ResourceId: {}", 
                    args, resourceType, resourceId);
        }
        return targetName;
    }
    
    private void persistOperationLog(HttpServletRequest request, OperationLog annotation,
            Long userId, String username, String resourceId, Object requestBody, Object result,
            String preOpTargetUsername, boolean success, String errorMessage, long startTime) {
        try {
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Attempting to log operation: success={}, executionTime={}ms", success, executionTime);
            
            String finalTargetUsername = preOpTargetUsername != null ? preOpTargetUsername : 
                    extractTargetUsername(new Object[]{requestBody}, annotation.resourceType(), resourceId, result);
            
            log.info("Logging operation with resourceId: {}, finalTargetUsername: {}", resourceId, finalTargetUsername);
            
            operationLogService.logUserOperation(
                    userId, username,
                    annotation.operationType(), annotation.operationName(),
                    annotation.resourceType(), resourceId,
                    request.getMethod(), request.getRequestURI(),
                    getClientIpAddress(request), request.getHeader("User-Agent"),
                    annotation.logRequest() ? requestBody : null,
                    annotation.logResponse() ? result : null,
                    success, errorMessage, finalTargetUsername,
                    annotation.category()
            );
            
            log.info("Operation log submitted successfully");
        } catch (Exception e) {
            log.error("Failed to log operation: {}", e.getMessage(), e);
        }
    }
    
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private String extractResourceId(Object[] args, int resourceIdIndex) {
        if (resourceIdIndex >= 0 && resourceIdIndex < args.length) {
            Object resourceId = args[resourceIdIndex];
            return resourceId != null ? resourceId.toString() : null;
        }
        return null;
    }
    
    /**
     * 提取目标资源名称（用于用户和部门操作）
     * 优先级：响应体 → 请求参数 → 数据库查询
     */
    private String extractTargetUsername(Object[] args, String resourceType, String resourceId, Object result) {
        log.info("Extracting target name for resourceType: {}, resourceId: {}", resourceType, resourceId);
        
        // 优先从响应体中提取名称（特别是部门操作）
        String fromResult = extractTargetNameFromResult(result, resourceType);
        if (fromResult != null) {
            return fromResult;
        }
        
        // 从请求参数中提取
        String fromArgs = extractTargetNameFromArgs(args, resourceType);
        if (fromArgs != null) {
            return fromArgs;
        }
        
        // 从数据库查询
        return fetchTargetNameFromDatabase(resourceType, resourceId);
    }
    
    private String extractTargetNameFromResult(Object result, String resourceType) {
        if (!"DEPARTMENT".equals(resourceType) || result == null) {
            return null;
        }
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            log.info("Checking result JSON for department name: {}", resultJson);
            
            JsonNode resultNode = objectMapper.readTree(resultJson);
            String name = extractNameFromNode(resultNode);
            if (name != null) {
                log.info("Extracted department name from response: {}", name);
                return name;
            }
        } catch (Exception e) {
            log.warn("Failed to extract department name from response: {}", e.getMessage());
        }
        return null;
    }
    
    private String extractTargetNameFromArgs(Object[] args, String resourceType) {
        for (Object arg : args) {
            if (arg == null) continue;
            try {
                String json = objectMapper.writeValueAsString(arg);
                JsonNode node = objectMapper.readTree(json);
                
                if ("USER".equals(resourceType) && node.has("username")) {
                    String username = node.get("username").asText();
                    log.info("Extracted username from request body: {}", username);
                    return username;
                }
                
                if ("DEPARTMENT".equals(resourceType)) {
                    String deptName = extractDepartmentNameFromNode(node);
                    if (deptName != null) {
                        log.info("Extracted department name from request body: {}", deptName);
                        return deptName;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract name from argument: {}", e.getMessage());
            }
        }
        return null;
    }
    
    private String extractNameFromNode(JsonNode node) {
        // 支持多种响应结构：{body: {data: {name}}}、{data: {name}}、{name}
        if (node.has("body") && node.get("body").has("data")) {
            JsonNode dataNode = node.get("body").get("data");
            if (dataNode.has("name")) return dataNode.get("name").asText();
        }
        if (node.has("data") && node.get("data").has("name")) {
            return node.get("data").get("name").asText();
        }
        if (node.has("name")) {
            return node.get("name").asText();
        }
        return null;
    }
    
    private String extractDepartmentNameFromNode(JsonNode node) {
        if (node.has("name")) return node.get("name").asText();
        if (node.has("departmentName")) return node.get("departmentName").asText();
        return null;
    }
    
    private String fetchTargetNameFromDatabase(String resourceType, String resourceId) {
        if (resourceId == null || resourceId.trim().isEmpty()) {
            log.warn("ResourceId is null or empty, cannot fetch name");
            return null;
        }
        
        try {
            Long id = Long.parseLong(resourceId.trim());
            if ("USER".equals(resourceType)) {
                return fetchUsernameFromDatabase(id);
            } else if ("DEPARTMENT".equals(resourceType)) {
                return fetchDepartmentNameFromDatabase(id);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid resourceId format: {}", resourceId);
        } catch (Exception e) {
            log.error("Failed to fetch name from DB for {} {}: {}", resourceType, resourceId, e.getMessage(), e);
        }
        return null;
    }
    
    private String fetchUsernameFromDatabase(Long id) {
        try {
            var user = userService.getUserByIdIncludeInactive(id);
            if (user != null) {
                log.info("Fetched username from DB: {} for userId: {}", user.getUsername(), id);
                return user.getUsername();
            }
            log.warn("User not found for userId: {}", id);
        } catch (Exception e) {
            log.error("Failed to fetch user with ID {}: {}", id, e.getMessage(), e);
        }
        return null;
    }
    
    private String fetchDepartmentNameFromDatabase(Long id) {
        try {
            var department = departmentService.getDepartmentById(id);
            if (department != null) {
                log.info("Fetched department name from DB: {} for departmentId: {}", department.getName(), id);
                return department.getName();
            }
            log.warn("Department not found for departmentId: {}", id);
        } catch (Exception e) {
            log.error("Failed to fetch department for departmentId {}: {}", id, e.getMessage(), e);
        }
        return null;
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
}
