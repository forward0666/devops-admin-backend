package com.backend.manage.controller.system;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.entity.system.RoleEntity;
import com.backend.manage.service.system.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 角色管理控制器
 * 处理角色相关的CRUD操作和业务逻辑
 * 使用 Java 21 风格
 */
@Slf4j
@RestController
@RequestMapping("/roles")
public class RoleController {

    @Autowired
    private RoleService roleService;

    /**
     * 获取所有角色列表接口
     */
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<RoleEntity>>> getAllRoles() {
        log.info("GET /roles - Fetching all roles");
        try {
            var roles = roleService.getAllRoles();
            log.info("Successfully retrieved {} roles", roles.size());
            return ResponseEntity.ok(ApiResponseDto.success("Roles retrieved successfully", roles));
        } catch (Exception e) {
            log.error("Error retrieving roles", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve roles: " + e.getMessage()));
        }
    }

    /**
     * 根据ID获取角色详情接口
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<RoleEntity>> getRoleById(@PathVariable Long id) {
        log.info("GET /roles/{} - Fetching role by ID", id);
        try {
            var role = roleService.getRoleById(id);
            if (role != null) {
                log.info("Successfully retrieved role: {}", role.getName());
                return ResponseEntity.ok(ApiResponseDto.success("Role retrieved successfully", role));
            } else {
                log.warn("Role not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Role not found"));
            }
        } catch (Exception e) {
            log.error("Error retrieving role with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve role: " + e.getMessage()));
        }
    }

    /**
     * 创建新角色接口
     */
    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建角色",
        resourceType = "ROLE",
        description = "创建新角色"
    )
    public ResponseEntity<ApiResponseDto<RoleEntity>> createRole(@Valid @RequestBody RoleEntity role) {
        log.info("POST /roles - Creating new role: {}", role.getName());
        try {
            var createdRole = roleService.createRole(role);
            log.info("Successfully created role with ID: {}", createdRole.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponseDto.success("Role created successfully", createdRole));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid role data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating role: {}", role.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to create role: " + e.getMessage()));
        }
    }

    /**
     * 更新角色信息接口
     */
    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新角色",
        resourceType = "ROLE",
        resourceIdIndex = 0,
        description = "更新角色信息"
    )
    public ResponseEntity<ApiResponseDto<RoleEntity>> updateRole(@PathVariable Long id, @Valid @RequestBody RoleEntity role) {
        log.info("PUT /roles/{} - Updating role", id);
        try {
            role.setId(id);
            var updatedRole = roleService.updateRole(role);
            if (updatedRole != null) {
                log.info("Successfully updated role: {}", updatedRole.getName());
                return ResponseEntity.ok(ApiResponseDto.success("Role updated successfully", updatedRole));
            } else {
                log.warn("Role not found for update with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Role not found"));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role data for update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid role data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating role with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to update role: " + e.getMessage()));
        }
    }

    /**
     * 删除角色接口
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除角色",
        resourceType = "ROLE",
        resourceIdIndex = 0,
        description = "删除角色"
    )
    public ResponseEntity<ApiResponseDto<Void>> deleteRole(@PathVariable Long id) {
        log.info("DELETE /roles/{} - Deleting role", id);
        try {
            boolean deleted = roleService.deleteRole(id);
            if (deleted) {
                log.info("Successfully deleted role with ID: {}", id);
                return ResponseEntity.ok(ApiResponseDto.success("Role deleted successfully", null));
            } else {
                log.warn("Role not found for deletion with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Role not found"));
            }
        } catch (IllegalStateException e) {
            log.warn("Cannot delete role with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponseDto.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting role with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to delete role: " + e.getMessage()));
        }
    }
}
