package com.backend.manage.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色实体类
 * 用于管理系统中的角色信息
 *
 * 设计特点：
 * 1. 使用 @Table 注解映射到数据库表 role
 * 2. 使用 @Id 注解标记主键
 * 3. 使用 Lombok @Data 注解自动生成 getter/setter
 * 4. 支持软删除（通过 deletedAt）
 * 5. 包含审计字段和用户统计
 *
 * @author Backend Team
 * @version 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("role")
public class RoleEntity {
    /**
     * 主键 ID
     */
    @Id
    private Long id;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色代码
     */
    private String code;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 状态
     * - active: 启用
     * - inactive: 禁用
     */
    private String status;

    /**
     * 用户数量
     */
    private Integer userCount;

    /**
     * 创建人 ID
     */
    private Long createdBy;

    /**
     * 更新人 ID
     */
    private Long updatedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     *
     * 软删除标记，非 null 表示已删除
     */
    private LocalDateTime deletedAt;

    /**
     * 权限列表
     */
    private List<String> permissions;

    /**
     * 创建角色
     *
     * @param name 角色名称
     * @param code 角色代码
     * @param description 角色描述
     */
    public RoleEntity(String name, String code, String description) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.status = "active";
        this.userCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
