package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponse;
import com.backend.manage.model.Role;
import com.backend.manage.service.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 角色管理控制器
 * 处理角色相关的CRUD操作和业务逻辑
 */
@RestController
@RequestMapping("/roles")
public class RoleController {

    private static final Logger logger = LoggerFactory.getLogger(RoleController.class);

    @Autowired
    private RoleService roleService;

    /**
     * 获取所有角色列表接口
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> getAllRoles() {
        logger.info("GET /roles - Fetching all roles");
        try {
            List<Role> roles = roleService.getAllRoles();
            logger.info("Successfully retrieved {} roles", roles.size());
            return ResponseEntity.ok(ApiResponse.success("Roles retrieved successfully", roles));
        } catch (Exception e) {
            logger.error("Error retrieving roles", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve roles: " + e.getMessage()));
        }
    }

    /**
     * 根据ID获取角色详情接口
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> getRoleById(@PathVariable Long id) {
        logger.info("GET /roles/{} - Fetching role by ID", id);
        try {
            Role role = roleService.getRoleById(id);
            if (role != null) {
                logger.info("Successfully retrieved role: {}", role.getName());
                return ResponseEntity.ok(ApiResponse.success("Role retrieved successfully", role));
            } else {
                logger.warn("Role not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Role not found"));
            }
        } catch (Exception e) {
            logger.error("Error retrieving role with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve role: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Role>> createRole(@Valid @RequestBody Role role) {
        logger.info("POST /roles - Creating new role: {}", role.getName());
        try {
            Role createdRole = roleService.createRole(role);
            logger.info("Successfully created role with ID: {}", createdRole.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Role created successfully", createdRole));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid role data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid role data: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating role: {}", role.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create role: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Role>> updateRole(@PathVariable Long id, @Valid @RequestBody Role role) {
        logger.info("PUT /roles/{} - Updating role", id);
        try {
            role.setId(id);
            Role updatedRole = roleService.updateRole(role);
            if (updatedRole != null) {
                logger.info("Successfully updated role: {}", updatedRole.getName());
                return ResponseEntity.ok(ApiResponse.success("Role updated successfully", updatedRole));
            } else {
                logger.warn("Role not found for update with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Role not found"));
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid role data for update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid role data: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating role with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update role: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        logger.info("DELETE /roles/{} - Deleting role", id);
        try {
            boolean deleted = roleService.deleteRole(id);
            if (deleted) {
                logger.info("Successfully deleted role with ID: {}", id);
                return ResponseEntity.ok(ApiResponse.success("Role deleted successfully", null));
            } else {
                logger.warn("Role not found for deletion with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Role not found"));
            }
        } catch (IllegalStateException e) {
            logger.warn("Cannot delete role with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting role with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete role: " + e.getMessage()));
        }
    }
}
