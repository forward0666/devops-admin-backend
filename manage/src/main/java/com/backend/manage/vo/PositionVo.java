package com.backend.manage.vo;

import com.backend.manage.entity.PositionEntity;
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
) {
    public static PositionVo fromEntity(PositionEntity entity) {
        return new PositionVo(
                entity.getId(),
                entity.getName(),
                entity.getCode(),
                entity.getDepartmentId(),
                entity.getDepartmentName(),
                entity.getLevel(),
                null,  // levelText
                entity.getDescription(),
                entity.getStatus(),
                entity.getUserCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                null,  // formattedDate
                null,  // formattedDateTime
                null,  // statusColor
                null   // statusText
        );
    }
}
