package com.backend.security.dto;

// 使用Java 21的record特性，简化不可变数据载体
public record VerificationCodeResponse(
    boolean success,
    String message,
    String codeId,
    String imageBase64
) {
    
    // 提供便利的静态工厂方法
    public static VerificationCodeResponse success(String message, String codeId, String imageBase64) {
        return new VerificationCodeResponse(true, message, codeId, imageBase64);
    }
    
    public static VerificationCodeResponse error(String message) {
        return new VerificationCodeResponse(false, message, null, null);
    }
}