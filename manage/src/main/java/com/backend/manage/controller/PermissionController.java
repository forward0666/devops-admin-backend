package com.backend.manage.controller;

import com.backend.utils.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class PermissionController {

    private final JdbcTemplate jdbc;

    /**
     * 获取当前用户的权限列表（前端用来控制菜单和按钮显隐）
     */
    @GetMapping("/admin/permission/mine")
    public ApiResponseDto<Map<String, Object>> getMyPermissions(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ApiResponseDto.error(401, "Unauthorized");
        }

        // 用户信息
        Map<String, Object> userInfo = jdbc.queryForMap(
            "SELECT id, username, role FROM login.user WHERE id = ?", userId);

        // 角色列表
        List<Map<String, Object>> roles = jdbc.queryForList(
            "SELECT r.id, r.name, r.code FROM sys_role r " +
            "JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?", userId);

        // 所有可见权限 code 列表
        List<String> permissions = new ArrayList<>();
        for (Map<String, Object> role : roles) {
            List<String> codes = jdbc.queryForList(
                "SELECT permission_code FROM sys_role_permission WHERE role_id = ?",
                String.class, role.get("id"));
            for (String code : codes) {
                if ("*".equals(code)) {
                    // 通配符 → 返回所有权限
                    List<String> allCodes = jdbc.queryForList(
                        "SELECT code FROM sys_permission", String.class);
                    permissions.addAll(allCodes);
                    permissions.add("*");
                    break;
                }
                permissions.add(code);
            }
        }

        // 菜单权限（用于前端动态路由）
        List<Map<String, Object>> menuPermissions = jdbc.queryForList(
            "SELECT code, name, module, parent_id FROM sys_permission WHERE type IN ('menu', 'button')");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", userInfo);
        result.put("roles", roles);
        result.put("permissions", permissions.stream().distinct().toList());
        result.put("menus", menuPermissions);

        return ApiResponseDto.success("获取成功", result);
    }

    private Long getUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null) return Long.parseLong(userId);

        // fallback: 从 security context 获取
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String uid = ((org.springframework.web.context.request.ServletRequestAttributes) attrs)
                .getRequest().getHeader("X-User-Id");
            if (uid != null) return Long.parseLong(uid);
        }
        return null;
    }
}