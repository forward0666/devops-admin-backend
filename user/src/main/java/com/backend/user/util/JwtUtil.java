package com.backend.user.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private Map<String, Object> getClaimsFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            log.error("Error extracting claims from token: {}", e.getMessage());
            return null;
        }
    }
}
