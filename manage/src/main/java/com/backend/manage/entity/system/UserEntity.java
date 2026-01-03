package com.backend.manage.entity.system;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 表示系统中的用户信息，对应数据库中的 users 表
 *
 * 设计特点：
 * 1. 纯 POJO 实体类，由 MyBatis 通过 XML resultMap 进行字段映射
 * 2. 使用 Lombok @Data 注解自动生成 getter/setter
 * 3. 包含认证信息和状态信息
 * 4. 包含登录历史和密码管理
 *
 * @author Backend Team
 * @version 2.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 用户名
     * 用于登录认证
     */
    private String username;

    /**
     * 密码
     * 加密存储
     */
    private String password;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * Telegram 用户名
     */
    private String tgUsername;

    /**
     * 全名
     */
    private String fullName;

    /**
     * 头像 URL
     */
    private String avatarUrl;

    /**
     * 部门 ID
     */
    private Long departmentId;

    /**
     * 职位
     */
    private String position;

    /**
     * 员工 ID
     */
    private String employeeId;

    /**
     * 角色
     */
    private String role;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建人 ID
     */
    private Long createdBy;

    /**
     * 更新人 ID
     */
    private Long updatedBy;

    /**
     * 激活状态
     */
    private boolean active;

    /**
     * 邮箱验证状态
     */
    private boolean emailVerified;

    /**
     * 手机验证状态
     */
    private boolean phoneVerified;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 最后登录 IP
     */
    private String lastLoginIp;

    /**
     * 登录次数
     */
    private Integer loginCount;

    /**
     * 密码修改时间
     */
    private LocalDateTime passwordChangedAt;
}
