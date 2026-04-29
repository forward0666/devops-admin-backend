package com.backend.manage.dto;

import com.backend.manage.entity.DepartmentEntity;

import java.time.LocalDateTime;
import java.util.List;

public record DepartmentResponseDto(
        Long id,
        String name,
        String description,
        Long managerId,
        String managerName,
        List<Object> users,
        List<UserSummaryDto> recentUsers,
        Integer userCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DepartmentResponseDto fromDepartment(DepartmentEntity department) {
        var recentUsers = department.getRecentUsers() != null
            ? department.getRecentUsers().stream()
                .map(UserSummaryDto::fromUser)
                .toList()
            : null;

        var users = department.getUsers() != null
            ? department.getUsers().stream().map(u -> (Object) u).toList()
            : null;

        return new DepartmentResponseDto(
                department.getId(),
                department.getName(),
                department.getDescription(),
                department.getManagerId(),
                null,
                users,
                recentUsers,
                department.getUserCount(),
                department.getCreatedAt(),
                department.getUpdatedAt()
        );
    }
}
