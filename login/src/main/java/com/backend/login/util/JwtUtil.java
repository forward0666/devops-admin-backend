package com.backend.login.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Validate JWT token by checking its structure
     * Note: This is a basic validation. Actual signature verification should be done by security service
     */
    public Boolean validateToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return false;
            }
            
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.error("Invalid JWT token structure");
                return false;
            }
            
            // Try to decode the payload to ensure it's valid
            Map<String, Object> claims = getClaimsFromToken(token);
            return claims != null && !claims.isEmpty();
            
        } catch (Exception e) {
            log.error("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract username from JWT token
     */
    public String getUsernameFromToken(String token) {
        try {
            Map<String, Object> claims = getClaimsFromToken(token);
            if (claims != null) {
                // Try different possible username fields
                if (claims.containsKey("username")) {
                    return (String) claims.get("username");
                } else if (claims.containsKey("sub")) {
                    return (String) claims.get("sub");
                } else if (claims.containsKey("subject")) {
                    return (String) claims.get("subject");
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting username from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract role from JWT token
     */
    public String getRoleFromToken(String token) {
        try {
            Map<String, Object> claims = getClaimsFromToken(token);
            if (claims != null) {
                if (claims.containsKey("role")) {
                    return (String) claims.get("role");
                } else if (claims.containsKey("authorities")) {
                    Object authorities = claims.get("authorities");
                    if (authorities instanceof String) {
                        return (String) authorities;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting role from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract user ID from JWT token
     */
    public Long getUserIdFromToken(String token) {
        try {
            Map<String, Object> claims = getClaimsFromToken(token);
            if (claims != null && claims.containsKey("userId")) {
                Object userIdObj = claims.get("userId");
                if (userIdObj instanceof Integer) {
                    return ((Integer) userIdObj).longValue();
                } else if (userIdObj instanceof Long) {
                    return (Long) userIdObj;
                } else if (userIdObj instanceof Number) {
                    return ((Number) userIdObj).longValue();
                } else if (userIdObj instanceof String) {
                    try {
                        return Long.parseLong((String) userIdObj);
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse userId as Long: {}", userIdObj);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting userId from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract email from JWT token
     */
    public String getEmailFromToken(String token) {
        try {
            Map<String, Object> claims = getClaimsFromToken(token);
            if (claims != null && claims.containsKey("email")) {
                return (String) claims.get("email");
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting email from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract all claims from JWT token payload
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getClaimsFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            
            // Decode payload (base64)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, Map.class);
            
        } catch (Exception e) {
            log.error("Error extracting claims from token: {}", e.getMessage());
            return null;
        }
    }
}
