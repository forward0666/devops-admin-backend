package com.backend.security.authorization;

import com.backend.utils.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @CheckPermission 注解的 AOP 拦截器
 * 在每个标注了注解的方法执行前校验权限
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    private final PermissionEvaluator permissionEvaluator;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    @Around("@annotation(checkPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, CheckPermission checkPermission) throws Throwable {
        // 获取当前用户ID（从请求上下文）
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ApiResponseDto.error(401, "Unauthorized");
        }

        // 解析资源表达式（支持 SpEL）
        String action = checkPermission.action();
        String resource = resolveExpression(checkPermission.resource(), joinPoint);

        // 校验权限
        if (!permissionEvaluator.hasPermission(userId, action, resource)) {
            log.warn("Permission denied: userId={}, action={}, resource={}", userId, action, resource);
            return ApiResponseDto.error(401, "权限不足");
        }

        return joinPoint.proceed();
    }

    /**
     * 解析 SpEL 表达式，如 "#dto.projectId" → 参数值
     */
    private String resolveExpression(String expression, ProceedingJoinPoint joinPoint) {
        if (!expression.contains("#")) return expression;
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext ctx = new StandardEvaluationContext();
        for (int i = 0; i < (paramNames != null ? paramNames.length : 0); i++) {
            ctx.setVariable(paramNames[i], args[i]);
        }

        try {
            return spelParser.parseExpression(expression).getValue(ctx, String.class);
        } catch (Exception e) {
            log.warn("Failed to resolve SpEL: {}", expression);
            return expression;
        }
    }

    private Long getCurrentUserId() {
        // 从请求头 X-User-Id 或 SecurityContext 获取
        // 由 Gateway AuthFilter 设置
        var request = ((org.springframework.web.context.request.ServletRequestAttributes)
            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes());
        if (request != null) {
            String userId = request.getRequest().getHeader("X-User-Id");
            if (userId != null) {
                return Long.parseLong(userId);
            }
        }
        return null;
    }
}