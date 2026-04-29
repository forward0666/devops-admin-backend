package com.backend.user.vo;

import com.backend.user.entity.UserEntity;
import java.time.LocalDateTime;

public record UserVo(
    Long id,
    String username,
    String email,
    String phone,
    String tgUsername,
    String fullName,
    String avatarUrl,
    Long departmentId,
    String position,
    String employeeId,
    String role,
    boolean active,
    boolean emailVerified,
    boolean phoneVerified,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserVo fromEntity(UserEntity user) {
        return new UserVo(
            user.getId(), user.getUsername(), user.getEmail(), user.getPhone(),
            user.getTgUsername(), user.getFullName(), user.getAvatarUrl(),
            user.getDepartmentId(), user.getPosition(), user.getEmployeeId(),
            user.getRole(), user.isActive(), user.isEmailVerified(), user.isPhoneVerified(),
            user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}
