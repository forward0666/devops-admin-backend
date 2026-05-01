package com.backend.user.vo;

import com.backend.user.entity.ProjectMemberEntity;
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
    public static ProjectMemberVo fromEntity(ProjectMemberEntity member) {
        return new ProjectMemberVo(
            member.getId(), member.getProjectId(), member.getUserId(),
            null, null, member.getProjectRole(),
            member.getPosition(), member.getStatus(), member.getSystemRole(),
            member.getEmail(), member.getPhone(), member.getTgUsername(),
            member.getDepartmentName(), member.getUserPosition(),
            member.getJoinedAt(), member.getCreatedAt()
        );
    }
}
