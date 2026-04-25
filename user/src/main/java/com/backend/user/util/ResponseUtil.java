package com.backend.user.util;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

public class ResponseUtil {

    public static ResponseEntity<Map<String, Object>> success(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    public static ResponseEntity<Map<String, Object>> page(String message, Page<?> page, int originalPage) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", page.getContent());
        response.put("totalElements", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
        response.put("currentPage", originalPage);
        response.put("pageSize", page.getSize());
        return ResponseEntity.ok(response);
    }

    public static ResponseEntity<Map<String, Object>> error(String message, int statusCode) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", statusCode);
        errorResponse.put("message", message);
        return ResponseEntity.status(statusCode).body(errorResponse);
    }
}
