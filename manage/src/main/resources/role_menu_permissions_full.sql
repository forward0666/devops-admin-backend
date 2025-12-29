-- ====================================================================
-- 角色菜单权限映射表（完整版）
-- 用于存储角色与菜单之间的访问权限映射关系
-- ====================================================================

DROP TABLE IF EXISTS role_menu_permissions;

CREATE TABLE IF NOT EXISTS role_menu_permissions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限映射ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_menu (role_id, menu_id) COMMENT '角色-菜单唯一索引，防止重复映射',
    KEY idx_role_id (role_id) COMMENT '角色ID索引',
    KEY idx_menu_id (menu_id) COMMENT '菜单ID索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单权限映射表';

-- ====================================================================
-- 说明
-- ====================================================================
-- 1. roles.id 是 bigint unsigned，所以 role_id 使用 bigint unsigned
-- 2. menus.id 是 bigint，所以 menu_id 使用 bigint
-- 3. 由于类型不一致（unsigned vs non-unsigned），暂时不添加外键约束
-- 4. 应用层（PermissionService）会验证角色和菜单的存在性

-- ====================================================================
-- 插入示例权限数据
-- 注意：根据实际的roles表ID调整role_id
-- ====================================================================

-- 假设管理员角色ID为1，给所有菜单权限
-- 你需要先查询roles表获取实际ID
-- SELECT id, code, name FROM roles;

-- 示例：为管理员角色(id=1)分配所有菜单权限
INSERT INTO role_menu_permissions (role_id, menu_id)
SELECT 1, id FROM menus
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- 示例：为DevOps管理员角色(id=2)分配特定菜单权限
-- 假设id=2是devops_admin
INSERT INTO role_menu_permissions (role_id, menu_id) VALUES
(2, 1),  -- 仪表盘
(2, 2),  -- 系统管理
(2, 3),  -- 用户管理
(2, 4),  -- 角色管理
(2, 5),  -- 菜单管理
(2, 6),  -- 部门管理
(2, 7),  -- 岗位管理
(2, 8),  -- 审计日志
(2, 9),  -- 操作日志
(2, 11)  -- 权限管理
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- ====================================================================
-- 验证数据
-- ====================================================================

SELECT '权限数据插入完成' AS message, COUNT(*) AS total_count FROM role_menu_permissions;

-- 查看角色权限分布
SELECT
    r.id AS role_id,
    r.code AS role_code,
    r.name AS role_name,
    COUNT(rmp.menu_id) AS menu_count
FROM roles r
LEFT JOIN role_menu_permissions rmp ON r.id = rmp.role_id
GROUP BY r.id, r.code, r.name
ORDER BY r.id;

-- 查看菜单使用情况
SELECT
    m.id AS menu_id,
    m.name AS menu_name,
    COUNT(rmp.role_id) AS role_count
FROM menus m
LEFT JOIN role_menu_permissions rmp ON m.id = rmp.menu_id
GROUP BY m.id, m.name
ORDER BY m.sort;
