package com.backend.manage.dto.system;

import java.time.LocalDateTime;
import java.util.List;

public record PermissionResponseDto(
        Long roleId,
        String roleName,
        String roleCode,
        List<Long> menuIds,
        List<String> menuNames,
        List<MenuPermissionType> menuPermissionTypes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record MenuPermissionType(
            Long menuId,
            String permissionType
    ) {}
}
