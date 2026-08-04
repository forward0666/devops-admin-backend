-- ========================================
-- 功能模块系统 (Module + RoleModule)
-- Admin 控制台模块化菜单和权限控制
-- ========================================

CREATE TABLE IF NOT EXISTS `module` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '显示名称',
    `code` VARCHAR(100) NOT NULL COMMENT '模块代码，如 system_user',
    `icon` VARCHAR(100) DEFAULT NULL COMMENT '图标类名',
    `route_prefix` VARCHAR(255) DEFAULT NULL COMMENT '路由前缀',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父级模块ID',
    `sort_order` INT DEFAULT 99 COMMENT '排序序号',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '模块描述',
    `category` VARCHAR(20) DEFAULT 'admin' COMMENT '分类: admin/user/devops',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_category` (`category`),
    KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='功能模块定义';

CREATE TABLE IF NOT EXISTS `role_module` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `module_id` BIGINT NOT NULL COMMENT '模块ID',
    `can_read` TINYINT(1) DEFAULT 1,
    `can_write` TYNYINT(1) DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_module` (`role_id`, `module_id`),
    KEY `idx_module` (`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-模块权限关联';

-- ========================================
-- 种子数据：Admin 控制台默认模块
-- ========================================

-- 顶级分组
INSERT INTO `module` (name, code, icon, route_prefix, parent_id, sort_order, category) VALUES
('Dashboard', 'admin_dashboard', 'bx-home', '/admin/dashboard', NULL, 1, 'admin'),
('System', 'admin_system', 'bx-cog', '/admin/system', NULL, 2, 'admin'),
('Project', 'admin_project', 'bx-folder', '/admin/project', NULL, 3, 'admin'),
('Monitor', 'admin_monitor', 'bx-wifi', '/admin/monitor', NULL, 4, 'admin'),
('Setting', 'admin_setting', 'bx-cog', '/admin/tools', NULL, 5, 'admin');

-- System 子菜单
INSERT INTO `module` (name, code, icon, route_prefix, parent_id, sort_order, category) VALUES
('User', 'admin_system_user', 'bx-user', '/admin/system/user', (SELECT id FROM `module` m WHERE m.code = 'admin_system'), 1, 'admin'),
('Department', 'admin_system_dept', 'bx-buildings', '/admin/system/dept', (SELECT id FROM `module` m WHERE m.code = 'admin_system'), 2, 'admin');

-- Monitor 子菜单
INSERT INTO `module` (name, code, icon, route_prefix, parent_id, sort_order, category) VALUES
('Online User', 'admin_monitor_online', 'bx-wifi', '/admin/monitor/online', (SELECT id FROM `module` m WHERE m.code = 'admin_monitor'), 1, 'admin'),
('Login Log', 'admin_monitor_login', 'bx-log-in', '/admin/monitor/loginlog', (SELECT id FROM `module` m WHERE m.code = 'admin_monitor'), 2, 'admin'),
('Operation Log', 'admin_monitor_operation', 'bx-list-ul', '/admin/monitor/operationlog', (SELECT id FROM `module` m WHERE m.code = 'admin_monitor'), 3, 'admin');

-- 给 admin 角色赋予所有模块权限
INSERT INTO `role_module` (role_id, module_id, can_read, can_write)
SELECT r.id, m.id, 1, 1
FROM `role` r
CROSS JOIN `module` m
WHERE r.code IN ('admin', 'sys_admin');