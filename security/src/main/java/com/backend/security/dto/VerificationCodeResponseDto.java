package com.backend.security.dto;

// 使用Java 21的record特性，简化不可变数据载体
public record VerificationCodeResponseDto(
    boolean success,
    String message,
    String codeId,
    String imageBase64
) {
    
    // 提供便利的静态工厂方法
    public static VerificationCodeResponseDto success(String message, String codeId, String imageBase64) {
        return new VerificationCodeResponseDto(true, message, codeId, imageBase64);
    }
    
    public static VerificationCodeResponseDto error(String message) {
        return new VerificationCodeResponseDto(false, message, null, null);
    }
}