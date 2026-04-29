package com.backend.login.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色实体类
 * 用于管理系统中的角色信息
 *
 * 设计特点：
 * 1. 纯 POJO 实体类，由 MyBatis 通过 XML resultMap 进行字段映射
 * 2. 使用 Lombok @Data 注解自动生成 getter/setter
 * 3. 支持软删除（通过 deletedAt）
 * 4. 包含审计字段和用户统计
 *
 * @author Backend Team
 * @version 2.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleEntity {
    /**
     * 主键 ID
     */
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
    @JsonProperty("userCount")
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
