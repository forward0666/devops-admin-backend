package com.backend.manage.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 职位实体类
 * 用于管理系统中的职位信息
 *
 * 设计特点：
 * 1. 纯 POJO 实体类，由 MyBatis 通过 XML resultMap 进行字段映射
 * 2. 使用 Lombok @Data 注解自动生成 getter/setter
 * 3. 支持软删除（通过 deletedAt）
 * 4. 包含职位层级和用户统计
 *
 * @author Backend Team
 * @version 2.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionEntity {
    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 职位名称
     */
    private String name;

    /**
     * 职位代码
     */
    private String code;

    /**
     * 部门 ID
     */
    private Long departmentId;

    /**
     * 部门名称
     */
    private String departmentName;

    /**
     * 职位级别
     */
    private Integer level;

    /**
     * 职位描述
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
     * 创建职位
     *
     * @param name 职位名称
     * @param code 职位代码
     * @param departmentId 部门 ID
     * @param level 职位级别
     * @param description 职位描述
     */
    public PositionEntity(String name, String code, Long departmentId, Integer level, String description) {
        this.name = name;
        this.code = code;
        this.departmentId = departmentId;
        this.level = level;
        this.description = description;
        this.status = "active";
        this.userCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
