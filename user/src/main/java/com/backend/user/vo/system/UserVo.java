package com.backend.user.vo.system;

import com.backend.user.entity.system.UserEntity;

public record UserVo(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        String tgUsername,
        String avatarUrl,
        String position,
        String employeeId,
        String role,
        Long departmentId,
        String department,
        boolean active,
        boolean emailVerified,
        boolean phoneVerified,
        String lastLoginAt,
        String lastLoginIp,
        Integer loginCount,
        String formattedDate,
        String formattedDateTime,
        String statusColor,
        String statusText
) {
    public static UserVo fromUser(UserEntity user) {
        return new UserVo(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getTgUsername(),
                user.getAvatarUrl(),
                user.getPosition(),
                user.getEmployeeId(),
                user.getRole(),
                user.getDepartmentId(),
                null,
                user.isActive(),
                user.isEmailVerified(),
                user.isPhoneVerified(),
                user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null,
                user.getLastLoginIp(),
                user.getLoginCount(),
                null,
                null,
                user.isActive() ? "green" : "red",
                user.isActive() ? "Active" : "Inactive"
        );
    }
}
