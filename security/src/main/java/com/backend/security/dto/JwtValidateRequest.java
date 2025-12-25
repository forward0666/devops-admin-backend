package com.backend.security.dto;

import jakarta.validation.constraints.NotBlank;

// 使用Java 21的record特性，简化不可变数据载体
public record JwtValidateRequest(
    @NotBlank
    String token
) {
    // 提供便利的静态工厂方法
    public static JwtValidateRequest of(String token) {
        return new JwtValidateRequest(token);
    }
}