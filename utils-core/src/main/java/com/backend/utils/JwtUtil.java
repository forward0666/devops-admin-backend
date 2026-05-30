package com.backend.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

/**
 * JWT Token 工具类
 * 基础结构校验 + payload 解码，不做签名验证
 */
@Slf4j
@Component
public class JwtUtil {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Boolean validateToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) return false;
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.error("Invalid JWT token structure");
                return false;
            }
            Map<String, Object> claims = getClaimsFromToken(token);
            return claims != null && !claims.isEmpty();
        } catch (Exception e) {
            log.error("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            Map<String, Object> claims = getClaimsFromToken(token);
            if (claims != null) {
                if (claims.containsKey("username")) return (String) claims.get("username");
                if (claims.containsKey("sub")) return (String) claims.get("sub");
                if (claims.containsKey("subject")) return (String) claims.get("subject");
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting username from token: {}", e.getMessage());
            return null;
        }
    }

    public String getRoleFromToken(String token) {
        try {
            Map<String, Object> claims = getClaimsFromToken(token);
            if (claims != null) {
                if (claims.containsKey("role")) return (String) claims.get("role");
                if (claims.containsKey("authorities")) {
                    Object authorities = claims.get("authorities");
                    if (authorities instanceof String s) return s;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting role from token: {}", e.getMessage());
            return null;
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            Map<String, Object> claims = getClaimsFromToken(token);
            if (claims != null && claims.containsKey("userId")) {
                Object userIdObj = claims.get("userId");
                if (userIdObj instanceof Integer i) return i.longValue();
                if (userIdObj instanceof Long l) return l;
                if (userIdObj instanceof Number n) return n.longValue();
                if (userIdObj instanceof String s) {
                    try { return Long.parseLong(s); } catch (NumberFormatException e) {
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

    public String getEmailFromToken(String token) {
        try {
            Map<String, Object> claims = getClaimsFromToken(token);
            if (claims != null && claims.containsKey("email")) return (String) claims.get("email");
            return null;
        } catch (Exception e) {
            log.error("Error extracting email from token: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getClaimsFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            log.error("Error extracting claims from token: {}", e.getMessage());
            return null;
        }
    }
}
