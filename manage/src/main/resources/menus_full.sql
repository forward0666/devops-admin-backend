-- ====================================================================
-- 菜单表创建脚本（完整版）
-- 用于存储系统菜单信息，支持树形结构
-- ====================================================================

DROP TABLE IF EXISTS menus;

CREATE TABLE IF NOT EXISTS menus (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    path VARCHAR(255) COMMENT '路由路径',
    icon VARCHAR(100) COMMENT '菜单图标',
    type VARCHAR(20) NOT NULL DEFAULT 'menu' COMMENT '类型: menu=菜单, button=按钮',
    sort INT DEFAULT 0 COMMENT '排序序号',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active=激活, inactive=停用',
    parent_id BIGINT COMMENT '父级菜单ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_status (status),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单表';

-- ====================================================================
-- 插入默认菜单数据
-- ====================================================================

TRUNCATE TABLE menus;

INSERT INTO menus (id, name, path, icon, type, sort, status, parent_id) VALUES
(1, '仪表盘', '/dashboard', 'i-heroicons-home', 'menu', 1, 'active', NULL),
(2, '系统管理', '/dashboard/system', 'i-heroicons-cog-8-tooth', 'menu', 2, 'active', NULL),
(3, '用户管理', '/dashboard/system/users', 'i-heroicons-users', 'menu', 1, 'active', 2),
(4, '角色管理', '/dashboard/system/roles', 'i-heroicons-shield-check', 'menu', 2, 'active', 2),
(5, '菜单管理', '/dashboard/system/menus', 'i-heroicons-bars-3', 'menu', 3, 'active', 2),
(6, '部门管理', '/dashboard/system/departments', 'i-heroicons-building-office', 'menu', 4, 'active', 2),
(7, '岗位管理', '/dashboard/system/positions', 'i-heroicons-briefcase', 'menu', 5, 'active', 2),
(8, '审计日志', '/dashboard/audits', 'i-heroicons-document-magnifying-glass', 'menu', 3, 'active', NULL),
(9, '操作日志', '/dashboard/audits/operations', 'i-heroicons-clipboard-document-list', 'menu', 1, 'active', 8),
(10, '系统设置', '/dashboard/settings', 'i-heroicons-cog', 'menu', 4, 'active', NULL),
(11, '权限管理', '/dashboard/system/permissions', 'i-heroicons-key', 'menu', 6, 'active', 2);

-- ====================================================================
-- 验证数据
-- ====================================================================

SELECT '菜单数据插入完成' AS message, COUNT(*) AS total_count FROM menus;
