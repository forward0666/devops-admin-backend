package com.backend.manage.vo.system;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限视图对象
 * 用于返回角色与菜单的权限映射关系
 *
 * 设计特点：
 * 1. 使用 Java 21 record 实现不可变数据结构
 * 2. 包含角色基本信息和权限列表
 * 3. 包含创建和更新时间用于审计
 * 4. 自动生成 getter 方法（如 roleId(), menuIds()）
 * 5. 自动实现 equals(), hashCode(), toString()
 *
 * @author Backend Team
 * @version 2.0.0
 */
public record PermissionVo(
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
         * 创建时间
         */
        LocalDateTime createdAt,

        /**
         * 更新时间
         */
        LocalDateTime updatedAt
) {}
