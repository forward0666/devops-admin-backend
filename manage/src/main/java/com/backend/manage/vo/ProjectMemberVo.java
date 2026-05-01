package com.backend.manage.vo;

import com.backend.manage.entity.ProjectMemberEntity;
import java.time.LocalDateTime;

public record ProjectMemberVo(
        Long id,
        Long projectId,
        Long userId,
        String username,
        String fullName,
        String projectRole,
        String position,
        String status,
        String systemRole,
        String email,
        String phone,
        String tgUsername,
        String departmentName,
        String userPosition,
        LocalDateTime joinedAt,
        LocalDateTime createdAt
) {
    public static ProjectMemberVo fromEntity(ProjectMemberEntity entity) {
        return new ProjectMemberVo(
                entity.getId(),
                entity.getProjectId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getFullName(),
                entity.getProjectRole(),
                entity.getPosition(),
                entity.getStatus(),
                entity.getSystemRole(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getTgUsername(),
                entity.getDepartmentName(),
                entity.getUserPosition(),
                entity.getJoinedAt(),
                entity.getCreatedAt()
        );
    }
}
