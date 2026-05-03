package com.backend.manage.vo;

import com.backend.manage.entity.RoleEntity;

public record RoleVo(
        Long id,
        String name,
        String code,
        String description,
        String status,
        Integer userCount,
        String[] permissions,
        String formattedDate,
        String formattedDateTime,
        String statusColor,
        String statusText,
        Integer permissionCount
) {
    public static RoleVo fromEntity(RoleEntity entity) {
        String[] perms = entity.getPermissions() != null
                ? entity.getPermissions().toArray(new String[0])
                : null;
        return new RoleVo(
                entity.getId(),
                entity.getName(),
                entity.getCode(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getUserCount(),
                perms,
                null,  // formattedDate
                null,  // formattedDateTime
                null,  // statusColor
                null,  // statusText
                perms != null ? perms.length : 0
        );
    }
}
