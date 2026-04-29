package com.backend.manage.vo;

import com.backend.manage.entity.DepartmentEntity;
import java.time.LocalDateTime;
import java.util.List;

public record DepartmentVo(
        Long id,
        String name,
        String description,
        Long managerId,
        String managerName,
        List<Object> users,
        List<Object> recentUsers,
        Integer userCount,
        LocalDateTime createdAt,
        String formattedDate,
        String formattedDateTime,
        String statusColor,
        String statusText
) {
    public static DepartmentVo fromEntity(DepartmentEntity entity) {
        return new DepartmentVo(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getManagerId(),
                null,  // managerName
                null,  // users
                null,  // recentUsers
                entity.getUserCount(),
                entity.getCreatedAt(),
                null,  // formattedDate
                null,  // formattedDateTime
                null,  // statusColor
                null   // statusText
        );
    }
}
