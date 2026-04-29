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
                null,  // systemRole - not in entity
                null,  // email - not in entity
                null,  // phone - not in entity
                null,  // tgUsername - not in entity
                null,  // departmentName - not in entity
                null,  // userPosition - not in entity
                entity.getJoinedAt(),
                entity.getCreatedAt()
        );
    }
}
