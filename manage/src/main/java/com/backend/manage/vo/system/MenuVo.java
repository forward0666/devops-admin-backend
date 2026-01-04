package com.backend.manage.vo.system;

import java.time.LocalDateTime;

public record MenuVo(
        Long id,
        Long menuId,
        String name,
        Long parentId,
        String path,
        String icon,
        String type,
        Integer sort,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String formattedDate,
        String formattedDateTime,
        String statusColor,
        String statusText
) {}
