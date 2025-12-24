package com.admin.security.dto;

import jakarta.validation.constraints.NotBlank;

public class JwtValidateRequest {
    @NotBlank
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}