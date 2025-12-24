package com.admin.security.dto;

public class VerificationCodeResponse {
    private boolean success;
    private String message;
    private String codeId;
    private String imageBase64;

    public VerificationCodeResponse() {}

    public VerificationCodeResponse(boolean success, String message, String codeId, String imageBase64) {
        this.success = success;
        this.message = message;
        this.codeId = codeId;
        this.imageBase64 = imageBase64;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCodeId() {
        return codeId;
    }

    public void setCodeId(String codeId) {
        this.codeId = codeId;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }
}