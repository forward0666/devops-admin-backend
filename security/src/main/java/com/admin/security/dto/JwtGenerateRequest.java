package com.admin.security.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public class JwtGenerateRequest {
    @NotBlank
    private String subject;
    
    private Map<String, Object> claims;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public void setClaims(Map<String, Object> claims) {
        this.claims = claims;
    }
}