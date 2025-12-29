package com.backend.security.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

// 使用Java 21的record特性，简化不可变数据载体
public record JwtGenerateRequestDto(
    @NotBlank
    String subject,
    
    Map<String, Object> claims
) {
    // 提供便利的静态工厂方法
    public static JwtGenerateRequestDto of(String subject, Map<String, Object> claims) {
        return new JwtGenerateRequestDto(subject, claims);
    }
    
    // 提供便利的静态工厂方法
    public static JwtGenerateRequestDto of(String subject) {
        return new JwtGenerateRequestDto(subject, Map.of());
    }
}