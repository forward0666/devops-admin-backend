package com.backend.manage.util;

import com.backend.manage.exception.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class AccessValidator {
    public static void validate(HttpServletRequest request, JwtUtil jwtUtil, String... allowedRoles) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new AccessDeniedException("未授权访问");
        }
        String role = jwtUtil.getRoleFromToken(token);
        if (!Arrays.asList(allowedRoles).contains(role)) {
            throw new AccessDeniedException("权限不足");
        }
    }

    private static String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
