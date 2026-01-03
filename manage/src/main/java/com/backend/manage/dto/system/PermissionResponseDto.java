package com.backend.manage.dto.system;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限响应数据传输对象
 * 用于返回角色与菜单的权限映射关系
 *
 * 设计特点：
 * 1. 使用 Java 21 record 实现不可变数据结构
 * 2. 包含角色基本信息和权限列表
 * 3. 包含创建和更新时间用于审计
 * 4. 自动生成 getter 方法（如 roleId(), menuIds()）
 * 5. 自动实现 equals(), hashCode(), toString()
 * 6. 统一使用 menuId 进行权限检查
 *
 * @author Backend Team
 * @version 3.0.0
 */
public record PermissionResponseDto(
        /**
         * 角色 ID
         */
        Long roleId,

        /**
         * 角色名称
         */
        String roleName,

        /**
         * 角色代码
         */
        String roleCode,

        /**
         * 菜单 ID 列表
         *
         * 该角色拥有的所有菜单权限 ID
         */
        List<Long> menuIds,

        /**
         * 菜单名称列表
         *
         * 该角色拥有的所有菜单权限名称
         */
        List<String> menuNames,

        /**
         * 菜单权限类型映射
         * Key: menuId, Value: permissionType (view/edit/all)
         */
        List<MenuPermissionType> menuPermissionTypes,

        /**
         * 创建时间
         */
        LocalDateTime createdAt,

        /**
         * 更新时间
         */
        LocalDateTime updatedAt
) {

    /**
     * 菜单权限类型记录
     * 用于表示每个菜单的权限类型
     */
    public record MenuPermissionType(
            /**
             * 菜单 ID
             */
            Long menuId,

            /**
             * 权限类型 (view-查看, edit-编辑, all-全部)
             */
            String permissionType
    ) {}

    /**
     * 创建权限响应的便捷方法
     *
     * @param roleId 角色 ID
     * @param roleName 角色名称
     * @param roleCode 角色代码
     * @param menuIds 菜单 ID 列表
     * @param menuNames 菜单名称列表
     * @return PermissionResponseDto 权限响应对象
     */
    public static PermissionResponseDto create(
            Long roleId,
            String roleName,
            String roleCode,
            List<Long> menuIds,
            List<String> menuNames
    ) {
        return new PermissionResponseDto(
                roleId,
                roleName,
                roleCode,
                menuIds,
                menuNames,
                List.of(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}

