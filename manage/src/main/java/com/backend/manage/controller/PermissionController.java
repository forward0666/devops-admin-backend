package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.dto.PermissionRequestDto;
import com.backend.manage.dto.PermissionResponseDto;
import com.backend.manage.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 权限管理控制器
 * 处理角色与菜单的权限映射关系
 * 使用 Java 21 风格
 */
@Slf4j
@RestController
@RequestMapping("/permissions")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    /**
     * 获取所有权限映射
     */
    @GetMapping("/mappings")
    public ResponseEntity<ApiResponseDto<List<PermissionResponseDto>>> getAllPermissionMappings() {
        log.info("GET /permissions/mappings - Fetching all permission mappings");
        try {
            var mappings = permissionService.getAllPermissionMappings();
            log.info("Successfully retrieved {} permission mappings", mappings.size());
            return ResponseEntity.ok(ApiResponseDto.success("Permission mappings retrieved successfully", mappings));
        } catch (Exception e) {
            log.error("Error retrieving permission mappings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve permission mappings: " + e.getMessage()));
        }
    }

    /**
     * 根据角色ID获取权限映射
     */
    @GetMapping("/role/{roleId}")
    public ResponseEntity<ApiResponseDto<PermissionResponseDto>> getPermissionMappingByRoleId(
            @PathVariable Long roleId) {
        log.info("GET /permissions/role/{} - Fetching permission mapping", roleId);
        try {
            var mapping = permissionService.getPermissionMappingByRoleId(roleId);
            return ResponseEntity.ok(ApiResponseDto.success("Permission mapping retrieved successfully", mapping));
        } catch (Exception e) {
            log.error("Error retrieving permission mapping for role ID: {}", roleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve permission mapping: " + e.getMessage()));
        }
    }

    /**
     * 更新角色的权限映射
     */
    @PutMapping("/role/{roleId}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新权限",
        resourceType = "PERMISSION",
        resourceIdIndex = 0,
        description = "更新角色菜单权限"
    )
    public ResponseEntity<ApiResponseDto<Void>> updatePermissionMapping(
            @PathVariable Long roleId,
            @Valid @RequestBody PermissionRequestDto request) {
        log.info("PUT /permissions/role/{} - Updating permission mapping", roleId);
        try {
            request.setRoleId(roleId);
            permissionService.updatePermissionMapping(request);
            log.info("Successfully updated permission mapping for role ID: {}", roleId);
            return ResponseEntity.ok(ApiResponseDto.success("Permission mapping updated successfully", null));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid permission mapping data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid permission mapping data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating permission mapping for role ID: {}", roleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to update permission mapping: " + e.getMessage()));
        }
    }

    /**
     * 删除角色的权限映射
     */
    @DeleteMapping("/role/{roleId}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除权限",
        resourceType = "PERMISSION",
        resourceIdIndex = 0,
        description = "删除角色菜单权限"
    )
    public ResponseEntity<ApiResponseDto<Void>> deletePermissionMapping(
            @PathVariable Long roleId) {
        log.info("DELETE /permissions/role/{} - Deleting permission mapping", roleId);
        try {
            permissionService.deletePermissionMapping(roleId);
            log.info("Successfully deleted permission mapping for role ID: {}", roleId);
            return ResponseEntity.ok(ApiResponseDto.success("Permission mapping deleted successfully", null));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role ID for deletion: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid role ID: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting permission mapping for role ID: {}", roleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to delete permission mapping: " + e.getMessage()));
        }
    }
}
