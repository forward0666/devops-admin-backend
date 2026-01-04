package com.backend.manage.vo.system;

import java.time.LocalDateTime;

public record PositionVo(
        Long id,
        String name,
        String code,
        Long departmentId,
        String department,
        Integer level,
        String levelText,
        String description,
        String status,
        Integer userCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String formattedDate,
        String formattedDateTime,
        String statusColor,
        String statusText
) {}
