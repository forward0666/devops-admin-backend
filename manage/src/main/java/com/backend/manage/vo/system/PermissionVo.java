package com.backend.manage.vo.system;

import java.time.LocalDateTime;
import java.util.List;

public record PermissionVo(
        Long roleId,
        String roleName,
        String roleCode,
        List<Long> menuIds,
        List<String> menuNames,
        List<MenuPermissionType> menuPermissionTypes,
        String[] permissions,
        Integer permissionCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record MenuPermissionType(
            Long menuId,
            String permissionType
    ) {}
}
