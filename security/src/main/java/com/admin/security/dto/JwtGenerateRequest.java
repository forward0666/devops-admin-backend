package com.admin.security.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

// 使用Java 21的record特性，简化不可变数据载体
public record JwtGenerateRequest(
    @NotBlank
    String subject,
    
    Map<String, Object> claims
) {
    // 提供便利的静态工厂方法
    public static JwtGenerateRequest of(String subject, Map<String, Object> claims) {
        return new JwtGenerateRequest(subject, claims);
    }
    
    // 提供便利的静态工厂方法
    public static JwtGenerateRequest of(String subject) {
        return new JwtGenerateRequest(subject, Map.of());
    }
}