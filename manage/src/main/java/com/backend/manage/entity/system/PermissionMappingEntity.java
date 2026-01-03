package com.backend.manage.entity.system;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色菜单权限映射实体类
 * 用于表示角色与菜单之间的访问权限映射关系
 *
 * 设计特点：
 * 1. 纯 POJO 实体类，由 MyBatis 通过 XML resultMap 进行字段映射
 * 2. 使用 Lombok @Data 注解自动生成 getter/setter
 * 3. 记录角色与菜单的关联关系
 * 4. 包含审计字段
 * 5. 统一使用 menuId 进行权限检查
 * 6. 复合主键：roleId + menuId
 *
 * @author Backend Team
 * @version 3.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionMappingEntity {
    /**
     * 角色 ID（复合主键的一部分）
     */
    private Long roleId;

    /**
     * 菜单 ID（复合主键的一部分，同时用于权限检查）
     */
    private Long menuId;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色代码
     */
    private String roleCode;

    /**
     * 菜单名称
     */
    private String menuName;

    /**
     * 权限类型 (view-查看, edit-编辑, all-全部)
     */
    private String permissionType;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建权限映射
     *
     * @param roleId 角色 ID
     * @param menuId 菜单 ID
     */
    public PermissionMappingEntity(Long roleId, Long menuId) {
        this.roleId = roleId;
        this.menuId = menuId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
