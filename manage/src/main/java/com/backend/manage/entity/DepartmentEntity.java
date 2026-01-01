package com.backend.manage.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门实体类
 * 用于管理系统中的部门信息
 *
 * 设计特点：
 * 1. 使用 @Table 注解映射到数据库表 department
 * 2. 使用 @Id 注解标记主键
 * 3. 使用 Lombok @Data 注解自动生成 getter/setter
 * 4. 包含审计字段（createdAt, updatedAt）
 * 5. 支持一对多关联关系（用户列表）
 *
 * @author Backend Team
 * @version 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("department")
public class DepartmentEntity {
    /**
     * 主键 ID
     */
    @Id
    private Long id;

    /**
     * 部门名称
     */
    private String name;

    /**
     * 部门描述
     */
    private String description;

    /**
     * 部门经理 ID
     */
    private Long managerId;

    /**
     * 部门用户数量
     */
    private Integer userCount = 0;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 部门用户列表
     */
    private List<UserEntity> users;

    /**
     * 最近加入的用户列表
     */
    private List<UserEntity> recentUsers;

    /**
     * 创建部门
     *
     * @param name 部门名称
     * @param description 部门描述
     */
    public DepartmentEntity(String name, String description) {
        this.name = name;
        this.description = description;
        this.userCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
