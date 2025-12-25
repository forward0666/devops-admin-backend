package com.admin.security.dto;

/**
 * 验证码验证请求DTO
 * 中文注释：用于接收验证码验证的请求参数
 * 包含验证码ID和用户输入的验证码
 */
public class VerificationCodeRequest {
    
    /**
     * 验证码ID
     * 中文注释：验证码的唯一标识，用于查找对应的验证码
     */
    private String codeId;
    
    /**
     * 用户输入的验证码
     * 中文注释：用户在前端输入的验证码内容
     */
    private String code;

    public VerificationCodeRequest() {}

    public VerificationCodeRequest(String codeId, String code) {
        this.codeId = codeId;
        this.code = code;
    }

    public String getCodeId() {
        return codeId;
    }

    public void setCodeId(String codeId) {
        this.codeId = codeId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}