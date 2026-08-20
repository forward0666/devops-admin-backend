package com.backend.manage.controller;

import com.backend.utils.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/role")
@RequiredArgsConstructor
public class RoleController {

    private final JdbcTemplate jdbc;

    @GetMapping("/list")
    public ApiResponseDto<List<Map<String, Object>>> listRoles() {
        List<Map<String, Object>> roles = jdbc.queryForList(
            "SELECT r.*, COUNT(rp.id) as perm_count " +
            "FROM sys_role r LEFT JOIN sys_role_permission rp ON r.id=rp.role_id " +
            "GROUP BY r.id ORDER BY r.id");
        return ApiResponseDto.success("获取成功", roles);
    }

    @GetMapping("/permissions")
    public ApiResponseDto<List<Map<String, Object>>> listPermissions() {
        List<Map<String, Object>> perms = jdbc.queryForList(
            "SELECT * FROM sys_permission ORDER BY module, sort_order");
        return ApiResponseDto.success("获取成功", perms);
    }

    @GetMapping("/{roleId}/permissions")
    public ApiResponseDto<List<Map<String, Object>>> getRolePermissions(@PathVariable Long roleId) {
        List<Map<String, Object>> perms = jdbc.queryForList(
            "SELECT * FROM sys_role_permission WHERE role_id = ?", roleId);
        return ApiResponseDto.success("获取成功", perms);
    }

    @PostMapping("/{roleId}/permissions")
    public ApiResponseDto<Void> updateRolePermissions(
            @PathVariable Long roleId,
            @RequestBody List<Map<String, Object>> permissions) {
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        for (Map<String, Object> p : permissions) {
            jdbc.update(
                "INSERT INTO sys_role_permission (role_id, permission_code, resource_scope, effect) VALUES (?, ?, ?, ?)",
                roleId, p.get("permission_code"), p.getOrDefault("resource_scope", "*"), p.getOrDefault("effect", "Allow"));
        }
        return ApiResponseDto.success("操作成功", null);
    }

    @GetMapping("/users")
    public ApiResponseDto<List<Map<String, Object>>> listUserRoles() {
        List<Map<String, Object>> users = jdbc.queryForList(
            "SELECT ur.user_id, ur.role_id, r.name as role_name, r.code as role_code " +
            "FROM sys_user_role ur JOIN sys_role r ON ur.role_id = r.id ORDER BY ur.user_id");
        return ApiResponseDto.success("获取成功", users);
    }

    @PostMapping("/users")
    public ApiResponseDto<Void> assignUserRole(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("user_id").toString());
        Long roleId = Long.valueOf(body.get("role_id").toString());
        jdbc.update("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return ApiResponseDto.success("操作成功", null);
    }

    @DeleteMapping("/users/{userId}/{roleId}")
    public ApiResponseDto<Void> removeUserRole(@PathVariable Long userId, @PathVariable Long roleId) {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = ? AND role_id = ?", userId, roleId);
        return ApiResponseDto.success("操作成功", null);
    }
}