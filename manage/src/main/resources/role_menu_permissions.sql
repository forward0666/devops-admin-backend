-- Role Menu Permissions Table
-- 角色菜单权限映射表
-- 用于存储角色与菜单之间的访问权限映射关系

CREATE TABLE IF NOT EXISTS `role_menu_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限映射ID',
  `role_id` bigint unsigned NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`) COMMENT '角色-菜单唯一索引，防止重复映射',
  KEY `idx_role_id` (`role_id`) COMMENT '角色ID索引',
  KEY `idx_menu_id` (`menu_id`) COMMENT '菜单ID索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单权限映射表';

-- 注意：
-- 1. roles.id 是 bigint unsigned，所以 role_id 使用 bigint unsigned
-- 2. menus.id 是 bigint，所以 menu_id 使用 bigint
-- 3. 由于类型不一致（unsigned vs non-unsigned），暂时不添加外键约束
-- 4. 如需添加外键约束，需要统一 roles.id 和 menus.id 的类型
-- 5. 应用层（PermissionService）会验证角色和菜单的存在性
