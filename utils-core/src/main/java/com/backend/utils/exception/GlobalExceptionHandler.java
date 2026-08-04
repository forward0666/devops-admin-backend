package com.backend.utils.exception;

import com.backend.utils.dto.ApiResponseDto;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Unified global exception handler for all REST controllers.
 * Produces ApiResponseDto formatted responses.
 *
 * Frontend reads: response.data.code (200/201 = success)
 * Error response: { "code": 4xx/5xx, "message": "...", "data": null }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleAccessDenied(AccessDeniedException e) {
        int status = "未授权访问".equals(e.getMessage()) ? 401 : 403;
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(status)
                .body(ApiResponseDto.error(status, e.getMessage()));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleBizException(BizException e) {
        log.warn("Biz exception: {}", e.getMessage());
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResponseDto.error(e.getHttpStatus(), e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("Data integrity violation: {}", e.getMessage());
        String msg = "操作失败，数据已存在或冲突";
        if (e.getMessage() != null) {
            if (e.getMessage().contains("Duplicate entry")) {
                msg = "数据已存在，请检查是否重复";
            } else if (e.getMessage().contains("foreign key constraint")) {
                msg = "操作失败，关联数据不存在";
            }
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponseDto.error(HttpStatus.CONFLICT.value(), msg));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest()
                .body(ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), "参数验证失败", errors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponseDto<Map<String, String>>> handleBind(BindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        log.warn("Binding failed: {}", errors);
        return ResponseEntity.badRequest()
                .body(ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), "参数绑定失败", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), "参数校验失败: " + e.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), "缺少必填参数: " + e.getParameterName()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), "参数错误: " + e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleRuntime(RuntimeException e) {
        log.error("Runtime exception: {}", e.getMessage(), e);
        int status = 500;
        if ("未授权访问".equals(e.getMessage())) status = 401;
        else if (e.getMessage() != null && e.getMessage().contains("权限不足")) status = 403;
        return ResponseEntity.status(status)
                .body(ApiResponseDto.error(status, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnhandled(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "系统错误，请联系管理员"));
    }
}