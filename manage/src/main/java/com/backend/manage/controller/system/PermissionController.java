package com.backend.manage.controller.system;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.dto.system.PermissionRequestDto;
import com.backend.manage.dto.system.PermissionResponseDto;
import com.backend.manage.service.system.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/permission")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<PermissionResponseDto>>> getAllPermissions() {
        log.info("GET /permission - Fetching all permissions");
        try {
            var mappings = permissionService.getAllPermissions();
            log.info("Successfully retrieved {} permissions", mappings.size());
            return ResponseEntity.ok(ApiResponseDto.success("Permissions retrieved successfully", mappings));
        } catch (Exception e) {
            log.error("Error retrieving permissions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve permissions: " + e.getMessage()));
        }
    }

    @GetMapping("/role/{roleId}")
    public ResponseEntity<ApiResponseDto<PermissionResponseDto>> getPermissionMappingByRoleId(
            @PathVariable Long roleId) {
        log.info("GET /permissions/role/{} - Fetching permission mapping", roleId);
        try {
            var mapping = permissionService.getPermissionsByRoleId(roleId);
            return ResponseEntity.ok(ApiResponseDto.success("Permissions retrieved successfully", mapping));
        } catch (Exception e) {
            log.error("Error retrieving permissions by role ID: {}", roleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve permissions: " + e.getMessage()));
        }
    }

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
            permissionService.updatePermissions(request);
            log.info("Successfully updated permissions for role ID: {}", roleId);
            return ResponseEntity.ok(ApiResponseDto.success("Permissions updated successfully", null));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid permission data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid permission data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating permissions for role ID: {}", roleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to update permissions: " + e.getMessage()));
        }
    }

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
