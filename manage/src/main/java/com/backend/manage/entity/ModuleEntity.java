package com.backend.manage.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 功能模块实体
 * 用于 admin 控制台的模块化菜单和权限控制
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleEntity {
    private Long id;
    private String name;          // 显示名称
    private String code;          // 模块代码，如 "system_user"
    private String icon;          // 图标类名，如 "bx-user"
    private String routePrefix;   // 路由前缀，如 "/admin/system/user"
    private Long parentId;        // 父级模块ID（树形结构）
    private Integer sortOrder;    // 排序序号
    private String description;   // 模块描述
    private String category;      // 分类：admin/user/devops
    private Boolean enabled;      // 是否启用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}