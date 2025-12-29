-- 角色表 (roles)
-- 用于存储系统中的角色信息
CREATE TABLE IF NOT EXISTS `roles` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `name` VARCHAR(100) NOT NULL COMMENT '角色名称',
  `code` VARCHAR(50) NOT NULL COMMENT '角色代码（唯一标识）',
  `description` TEXT COMMENT '角色描述',
  `status` ENUM('active', 'inactive') NOT NULL DEFAULT 'active' COMMENT '状态：active-启用，inactive-禁用',
  `user_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '使用该角色的用户数量',
  `created_by` BIGINT UNSIGNED COMMENT '创建人ID',
  `updated_by` BIGINT UNSIGNED COMMENT '更新人ID',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TIMESTAMP NULL COMMENT '删除时间（软删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 角色权限关联表 (role_permissions)
-- 用于存储角色和权限的多对多关系
CREATE TABLE IF NOT EXISTS `role_permissions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  `permission_code` VARCHAR(100) NOT NULL COMMENT '权限代码',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_code`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 插入默认角色数据
INSERT INTO `roles` (`name`, `code`, `description`, `status`, `user_count`) VALUES
('Administrator', 'admin', '超级管理员，拥有系统所有权限', 'active', 0),
('DevOps Admin', 'devops_admin', 'DevOps管理员，负责运维和部署相关操作', 'active', 0),
('System Admin', 'sys_admin', '系统管理员，负责系统配置和监控', 'active', 0),
('User Admin', 'user_admin', '用户管理员，负责用户和权限管理', 'active', 0),
('Editor', 'editor', '编辑员，可以编辑和创建内容', 'active', 0),
('Viewer', 'viewer', '查看员，只读权限', 'active', 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 插入默认权限数据
-- 管理员角色的所有权限
INSERT INTO `role_permissions` (`role_id`, `permission_code`)
SELECT `id`, 'admin'
FROM `roles`
WHERE `code` = 'admin';

-- DevOps管理员权限
INSERT INTO `role_permissions` (`role_id`, `permission_code`)
SELECT `id`, 'devops_admin'
FROM `roles`
WHERE `code` = 'devops_admin';

-- 系统管理员权限
INSERT INTO `role_permissions` (`role_id`, `permission_code`)
SELECT `id`, 'sys_admin'
FROM `roles`
WHERE `code` = 'sys_admin';

-- 用户管理权限
INSERT INTO `role_permissions` (`role_id`, `permission_code`)
SELECT `id`, 'user_management'
FROM `roles`
WHERE `code` IN ('admin', 'user_admin', 'devops_admin', 'sys_admin');

-- 角色管理权限
INSERT INTO `role_permissions` (`role_id`, `permission_code`)
SELECT `id`, 'role_management'
FROM `roles`
WHERE `code` IN ('admin', 'user_admin');

-- 菜单管理权限
INSERT INTO `role_permissions` (`role_id`, `permission_code`)
SELECT `id`, 'menu_management'
FROM `roles`
WHERE `code` IN ('admin', 'user_admin');

-- 部门管理权限
INSERT INTO `role_permissions` (`role_id`, `permission_code`)
SELECT `id`, 'department_management'
FROM `roles`
WHERE `code` IN ('admin', 'user_admin', 'devops_admin', 'sys_admin');

-- 职位管理权限
INSERT INTO `role_permissions` (`role_id`, `permission_code`)
SELECT `id`, 'position_management'
FROM `roles`
WHERE `code` IN ('admin', 'user_admin');
