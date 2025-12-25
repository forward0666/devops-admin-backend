package com.backend.manage.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
@Component
public class AccessValidator {
    public static void validate(HttpServletRequest request, JwtUtil jwtUtil, String... allowedRoles) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new RuntimeException("未授权访问");
        }
        String role = jwtUtil.getRoleFromToken(token);
        if (!Arrays.asList(allowedRoles).contains(role)) {
            throw new RuntimeException("权限不足");
        }
    }

    public static void validateUserOrAdmin(HttpServletRequest request, JwtUtil jwtUtil, Long targetUserId) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new RuntimeException("未授权访问");
        }

        String role = jwtUtil.getRoleFromToken(token);
        Long currentUserId = jwtUtil.getUserIdFromToken(token);

        if (!"admin".equals(role) && !"sys_admin".equals(role) && !targetUserId.equals(currentUserId)) {
            throw new RuntimeException("权限不足");
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

