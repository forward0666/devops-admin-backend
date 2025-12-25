package com.admin.security.dto;

import java.util.Map;

// 使用Java 21的record特性，简化不可变数据载体
public record JwtResponse(
    Boolean success, 
    String message, 
    String token, 
    Map<String, Object> data
) {
    
    // 提供便利的静态工厂方法
    public static JwtResponse success(String message, String token, Map<String, Object> data) {
        return new JwtResponse(true, message, token, data);
    }
    
    public static JwtResponse success(String message, String token) {
        return new JwtResponse(true, message, token, null);
    }
    
    public static JwtResponse error(String message) {
        return new JwtResponse(false, message, null, null);
    }
    
    public static JwtResponse error(String message, String token) {
        return new JwtResponse(false, message, token, null);
    }
}