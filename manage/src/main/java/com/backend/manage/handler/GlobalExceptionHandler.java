package com.backend.manage.handler;

import com.backend.manage.util.ResponseUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

//@RestControllerAdvice
//public class GlobalExceptionHandler {
//    @ExceptionHandler(RuntimeException.class)
//    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
//        if ("未授权访问".equals(e.getMessage())) {
//            return ResponseUtil.error(e.getMessage(), 401);
//        } else if (e.getMessage().contains("权限不足")) {
//            return ResponseUtil.error(e.getMessage(), 403);
//        }
//        return ResponseUtil.error("服务器错误: " + e.getMessage(), 500);
//    }
//}

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        if ("未授权访问".equals(e.getMessage())) {
            return ResponseUtil.error(e.getMessage(), 401);
        } else if (e.getMessage().contains("权限不足")) {
            return ResponseUtil.error(e.getMessage(), 403);
        }
        return ResponseUtil.error("服务器错误: " + e.getMessage(), 500);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        return ResponseUtil.error("服务器内部错误: " + e.getMessage(), 500);
    }
}


