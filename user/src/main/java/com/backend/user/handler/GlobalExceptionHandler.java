package com.backend.user.handler;

import com.backend.user.exception.AccessDeniedException;
import com.backend.user.util.ResponseUtil;
import com.backend.user.exception.AccessDeniedException;
import com.backend.user.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理应用中的各种异常，返回标准化的错误响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理权限拒绝异常（不打印堆栈）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        String message = e.getMessage();
        if ("未授权访问".equals(message)) {
            return ResponseUtil.error(message, 401);
        }
        return ResponseUtil.error(message, 403);
    }

    /**
     * 处理数据完整性违规异常（如重复键）
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.error("Data integrity violation: {}", e.getMessage());

        String message = "操作失败，数据已存在或冲突";
        if (e.getMessage() != null) {
            if (e.getMessage().contains("Duplicate entry")) {
                message = "数据已存在，请检查是否重复";
            } else if (e.getMessage().contains("foreign key constraint")) {
                message = "操作失败，关联数据不存在";
            }
        }

        return ResponseUtil.error(message, HttpStatus.CONFLICT.value());
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", errors);

        Map<String, Object> response = new HashMap<>();
        response.put("code", HttpStatus.BAD_REQUEST.value());
        response.put("message", "参数验证失败");
        response.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Binding failed: {}", errors);

        Map<String, Object> response = new HashMap<>();
        response.put("code", HttpStatus.BAD_REQUEST.value());
        response.put("message", "参数绑定失败");
        response.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return ResponseUtil.error("参数错误: " + e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception: {}", e.getMessage(), e);

        if ("未授权访问".equals(e.getMessage())) {
            return ResponseUtil.error(e.getMessage(), 401);
        } else if (e.getMessage().contains("权限不足")) {
            return ResponseUtil.error(e.getMessage(), 403);
        }
        return ResponseUtil.error("服务器错误: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseUtil.error("系统错误，请联系管理员", HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
