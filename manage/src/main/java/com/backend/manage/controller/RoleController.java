package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.manage.entity.RoleEntity;
import com.backend.manage.service.RoleService;
import com.backend.manage.vo.RoleVo;
import com.backend.utils.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<RoleVo>>> getAllRoles() {
        var roles = roleService.getAllRoles();
        List<RoleVo> result = roles.stream().map(RoleVo::fromEntity).toList();
        return ResponseEntity.ok(ApiResponseDto.success("Roles retrieved successfully", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<RoleVo>> getRoleById(@PathVariable Long id) {
        var role = roleService.getRoleById(id);
        if (role == null) throw new BizException(404, "Role not found");
        return ResponseEntity.ok(ApiResponseDto.success("Role retrieved successfully", RoleVo.fromEntity(role)));
    }

    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建角色",
        resourceType = "ROLE",
        description = "创建新角色"
    )
    public ResponseEntity<ApiResponseDto<RoleVo>> createRole(@Valid @RequestBody RoleEntity role) {
        var createdRole = roleService.createRole(role);
        return ResponseEntity.ok(ApiResponseDto.success("Role created successfully", RoleVo.fromEntity(createdRole)));
    }

    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新角色",
        resourceType = "ROLE",
        resourceIdIndex = 0,
        description = "更新角色信息"
    )
    public ResponseEntity<ApiResponseDto<RoleVo>> updateRole(@PathVariable Long id, @Valid @RequestBody RoleEntity role) {
        role.setId(id);
        var updatedRole = roleService.updateRole(role);
        if (updatedRole == null) throw new BizException(404, "Role not found");
        return ResponseEntity.ok(ApiResponseDto.success("Role updated successfully", RoleVo.fromEntity(updatedRole)));
    }

    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除角色",
        resourceType = "ROLE",
        resourceIdIndex = 0,
        description = "删除角色"
    )
    public ResponseEntity<ApiResponseDto<Void>> deleteRole(@PathVariable Long id) {
        boolean deleted = roleService.deleteRole(id);
        if (!deleted) throw new BizException(404, "Role not found");
        return ResponseEntity.ok(ApiResponseDto.success("Role deleted successfully", null));
    }
}