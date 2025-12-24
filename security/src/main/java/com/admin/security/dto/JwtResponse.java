package com.admin.security.dto;

import java.util.Map;

public class JwtResponse {
    private Boolean success;
    private String message;
    private String token;
    private Map<String, Object> data;

    public JwtResponse(Boolean success, String message, String token, Map<String, Object> data) {
        this.success = success;
        this.message = message;
        this.token = token;
        this.data = data;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}