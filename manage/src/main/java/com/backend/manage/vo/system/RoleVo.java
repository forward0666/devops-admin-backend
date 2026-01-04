package com.backend.manage.vo.system;

import java.time.LocalDateTime;

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
) {}
