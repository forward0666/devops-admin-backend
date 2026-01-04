package com.backend.manage.util;

import com.backend.manage.exception.AccessDeniedException;
import com.backend.manage.service.system.PermissionService;
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

    public static void validateMenuPermission(HttpServletRequest request, JwtUtil jwtUtil, PermissionService permissionService, Long menuId) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new AccessDeniedException("未授权访问");
        }
        String roleCode = jwtUtil.getRoleFromToken(token);

        if (roleCode == null || roleCode.isEmpty()) {
            throw new AccessDeniedException("无法获取用户角色信息");
        }

        var allMappings = permissionService.getAllPermissions();
        var roleMapping = allMappings.stream()
                .filter(m -> m.roleCode() != null && m.roleCode().equals(roleCode))
                .findFirst()
                .orElse(null);

        if (roleMapping == null) {
            throw new AccessDeniedException("未找到角色权限配置");
        }

        boolean hasPermission = roleMapping.menuIds().stream()
                .anyMatch(id -> id != null && id.equals(menuId));

        if (!hasPermission) {
            throw new AccessDeniedException("权限不足");
        }
    }

    public static void validateUserOrAdmin(HttpServletRequest request, JwtUtil jwtUtil, Long targetUserId) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new AccessDeniedException("未授权访问");
        }

        String role = jwtUtil.getRoleFromToken(token);
        Long currentUserId = jwtUtil.getUserIdFromToken(token);

        if (!"admin".equals(role) && !"sys_admin".equals(role) && !targetUserId.equals(currentUserId)) {
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
