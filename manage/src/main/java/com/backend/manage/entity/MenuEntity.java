package com.backend.manage.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单实体类
 * 用于表示系统菜单信息，支持树形结构
 *
 * 设计特点：
 * 1. 使用 @Table 注解映射到数据库表 menu
 * 2. 使用 @Id 注解标记主键
 * 3. 使用 Lombok @Data 注解自动生成 getter/setter
 * 4. 支持树形结构（通过 parentId）
 * 5. 包含排序和状态字段
 * 6. 统一使用 id 进行权限检查
 *
 * @author Backend Team
 * @version 3.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("menus")
public class MenuEntity {
    /**
     * 主键菜单 ID（同时用于权限检查）
     */
    @Id
    private Long menuId;

    /**
     * 菜单名称
     */
    private String name;

    /**
     * 菜单路径
     */
    private String path;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 菜单类型
     * - menu: 菜单项
     * - button: 按钮
     */
    private String type;

    /**
     * 排序号
     */
    private Integer sort;

    /**
     * 状态
     * - active: 启用
     * - inactive: 禁用
     */
    private String status;

    /**
     * 父菜单 ID
     */
    private Long parentId;

    /**
     * 父菜单名称
     */
    private String parentName;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 子菜单列表
     */
    private List<MenuEntity> children;

    /**
     * 创建菜单
     *
     * @param name 菜单名称
     * @param path 菜单路径
     * @param icon 菜单图标
     * @param type 菜单类型
     * @param sort 排序号
     * @param status 状态
     * @param parentId 父菜单 ID（统一使用 id 进行权限检查）
     */
    public MenuEntity(String name, String path, String icon, String type, Integer sort, String status, Long parentId) {
        this.name = name;
        this.path = path;
        this.icon = icon;
        this.type = type;
        this.sort = sort;
        this.status = status;
        this.parentId = parentId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
