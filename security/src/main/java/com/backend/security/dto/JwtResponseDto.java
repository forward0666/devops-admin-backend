package com.backend.security.dto;

import java.util.Map;

// 使用Java 21的record特性，简化不可变数据载体
public record JwtResponseDto(
    Boolean success, 
    String message, 
    String token, 
    Map<String, Object> data
) {
    
    // 提供便利的静态工厂方法
    public static JwtResponseDto success(String message, String token, Map<String, Object> data) {
        return new JwtResponseDto(true, message, token, data);
    }
    
    public static JwtResponseDto success(String message, String token) {
        return new JwtResponseDto(true, message, token, null);
    }
    
    public static JwtResponseDto error(String message) {
        return new JwtResponseDto(false, message, null, null);
    }
    
    public static JwtResponseDto error(String message, String token) {
        return new JwtResponseDto(false, message, token, null);
    }
}