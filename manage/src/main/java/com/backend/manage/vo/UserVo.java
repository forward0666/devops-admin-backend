package com.backend.manage.vo;

import com.backend.manage.entity.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户视图对象
 * 用于返回简化的用户信息
 * 使用 Java 21 风格
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVo {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String tgUsername;
    private String avatarUrl;
    private String position;
    private String employeeId;
    private String role;
    private Long departmentId;
    private String department;
    private boolean active;
    private boolean emailVerified;
    private boolean phoneVerified;
    private String lastLoginAt;
    private String lastLoginIp;
    private Integer loginCount;

    /**
     * 从用户实体转换为用户视图对象
     * @param user 用户实体
     * @return 用户视图对象
     */
    public static UserVo fromUser(UserEntity user) {
        var vo = new UserVo();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setFullName(user.getFullName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setTgUsername(user.getTgUsername());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPosition(user.getPosition());
        vo.setEmployeeId(user.getEmployeeId());
        vo.setRole(user.getRole());
        vo.setDepartmentId(user.getDepartmentId());
        vo.setActive(user.isActive());
        vo.setEmailVerified(user.isEmailVerified());
        vo.setPhoneVerified(user.isPhoneVerified());
        return vo;
    }
}

