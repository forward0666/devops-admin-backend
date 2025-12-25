package com.admin.security.dto;

/**
 * 验证码验证请求DTO
 * 中文注释：用于接收验证码验证的请求参数
 * 包含验证码ID和用户输入的验证码
 * 
 * 使用Java 21的record特性，简化不可变数据载体
 */
public record VerificationCodeRequest(
    /**
     * 验证码ID
     * 中文注释：验证码的唯一标识，用于查找对应的验证码
     */
    String codeId,
    
    /**
     * 用户输入的验证码
     * 中文注释：用户在前端输入的验证码内容
     */
    String code
) {
    /**
     * 创建一个空请求的静态工厂方法
     */
    public static VerificationCodeRequest empty() {
        return new VerificationCodeRequest(null, null);
    }
    
    /**
     * 验证请求是否有效（两个字段都不为null）
     */
    public boolean isValid() {
        return codeId != null && !codeId.trim().isEmpty() && 
               code != null && !code.trim().isEmpty();
    }
}