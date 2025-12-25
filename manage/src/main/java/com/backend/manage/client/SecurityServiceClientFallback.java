package com.backend.manage.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Fallback implementation for SecurityServiceClient
 * Used when the security service is unavailable
 */
//@Component
public class SecurityServiceClientFallback implements SecurityServiceClient {
    
    @Override
    public Map<String, Object> verifyCode(Map<String, String> request) {
        // Development mode fallback - allow verification to pass
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Fallback: Verification code accepted (development mode)");
        
        return result;
    }
    
    @Override
    public Map<String, Object> generateToken(Map<String, Object> request) {
        // Development mode fallback - generate mock token
        Map<String, Object> result = new HashMap<>();
        String username = request.get("subject") != null ? request.get("subject").toString() : "unknown";
        String mockToken = "mock-jwt-token-" + username + "-" + System.currentTimeMillis();
        result.put("token", mockToken);
        result.put("success", true);
        result.put("message", "Token generated (fallback)");
        
        return result;
    }
    
    @Override
    public Map<String, Object> validateToken(Map<String, String> request) {
        // Development mode fallback - validate mock tokens
        Map<String, Object> result = new HashMap<>();
        String token = request.get("token");
        
        if (token != null && token.startsWith("mock-jwt-token-")) {
            result.put("valid", true);
            result.put("success", true);
            result.put("message", "Token validated (fallback)");
        } else {
            result.put("valid", false);
            result.put("success", false);
            result.put("message", "Invalid token (fallback)");
        }
        
        return result;
    }
}
