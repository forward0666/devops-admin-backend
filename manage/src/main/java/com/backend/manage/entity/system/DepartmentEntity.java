package com.backend.manage.entity.system;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门实体类
 * 用于管理系统中的部门信息
 *
 * 设计特点：
 * 1. 纯 POJO 实体类，由 MyBatis 通过 XML resultMap 进行字段映射
 * 2. 使用 Lombok @Data 注解自动生成 getter/setter
 * 3. 包含审计字段（createdAt, updatedAt）
 * 4. 支持一对多关联关系（用户列表）
 *
 * @author Backend Team
 * @version 2.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentEntity {
    /**
     * 主键 ID
     */
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
     * 父部门 ID（支持树形结构）
     */
    private Long parentId;

    /**
     * 部门类型（office/department/team）
     */
    private String type;

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
     * 子部门列表（树形结构）
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    private List<DepartmentEntity> children;

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
