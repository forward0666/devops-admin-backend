package com.backend.manage.vo;

import com.backend.manage.entity.UserEntity;
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
        boolean locked,
        boolean emailVerified,
        boolean phoneVerified,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String source
) {
    public static UserVo fromEntity(UserEntity entity) {
        return fromEntity(entity, false);
    }

    public static UserVo fromEntity(UserEntity entity, boolean locked) {
        return new UserVo(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getTgUsername(),
                entity.getFullName(),
                entity.getAvatarUrl(),
                entity.getDepartmentId(),
                entity.getPosition(),
                entity.getEmployeeId(),
                entity.getRole(),
                entity.isActive(),
                locked,
                entity.isEmailVerified(),
                entity.isPhoneVerified(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getSource()
        );
    }
}
