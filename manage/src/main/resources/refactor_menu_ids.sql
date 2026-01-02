-- ====================================================================
-- 重新规划 menu_id，使用分层ID体系
-- ====================================================================
--
-- 规则：
-- - 一级菜单: 1-100
-- - 二级菜单: 101-999 (按模块划分)
--   - 系统管理: 201-299
--   - 审计日志: 301-399
--   - 系统设置: 401-499
-- ====================================================================

-- 步骤 1: 更新 menus 表的 ID，重新分配
-- 注意：由于 MySQL 不支持直接修改自增主键，我们需要删除后重新插入

-- 临时存储当前菜单数据
CREATE TEMPORARY TABLE temp_menus AS
SELECT
    id AS old_id,
    name,
    path,
    icon,
    type,
    sort,
    status,
    parent_id,
    created_at,
    updated_at,
    CASE
        WHEN id = 1 THEN 1
        WHEN id = 2 THEN 2
        WHEN id = 8 THEN 3
        WHEN id = 10 THEN 4
        WHEN id = 3 THEN 201
        WHEN id = 4 THEN 202
        WHEN id = 5 THEN 203
        WHEN id = 6 THEN 204
        WHEN id = 7 THEN 205
        WHEN id = 11 THEN 206
        WHEN id = 9 THEN 301
        WHEN id = 12 THEN 401
        ELSE id
    END AS new_id
FROM menus;

-- 删除旧的权限映射
DELETE FROM permissions;

-- 删除旧菜单
TRUNCATE TABLE menus;

-- 插入新菜单（使用新的ID体系）
INSERT INTO menus (id, name, path, icon, type, sort, status, parent_id, created_at, updated_at)
SELECT
    new_id AS id,
    name,
    path,
    icon,
    type,
    sort,
    CASE
        WHEN new_id BETWEEN 201 AND 299 THEN 2  -- 系统管理的子菜单
        WHEN new_id BETWEEN 301 AND 399 THEN 3  -- 审计日志的子菜单
        WHEN new_id BETWEEN 401 AND 499 THEN 4  -- 系统设置的子菜单
        ELSE NULL
    END AS parent_id,
    created_at,
    updated_at
FROM temp_menus;

-- 重置自增ID
ALTER TABLE menus AUTO_INCREMENT = 500;

-- 步骤 2: 重新插入权限数据
-- admin 角色 (role_id=1) 拥有所有菜单权限
INSERT INTO permissions (role_id, menu_id, permission_type, created_at, updated_at)
SELECT
    1 AS role_id,
    id AS menu_id,
    'all' AS permission_type,
    NOW() AS created_at,
    NOW() AS updated_at
FROM menus;

-- sys_admin 角色 (role_id=3) 拥有特定权限
INSERT INTO permissions (role_id, menu_id, permission_type, created_at, updated_at)
VALUES
(3, 1, 'edit', NOW(), NOW()),     -- 仪表盘
(3, 2, 'edit', NOW(), NOW()),     -- 系统管理
(3, 3, 'edit', NOW(), NOW()),     -- 审计日志
(3, 4, 'edit', NOW(), NOW()),     -- 系统设置
(3, 201, 'edit', NOW(), NOW()),   -- 用户管理
(3, 202, 'edit', NOW(), NOW()),   -- 角色管理
(3, 203, 'edit', NOW(), NOW()),   -- 菜单管理
(3, 204, 'edit', NOW(), NOW()),   -- 部门管理
(3, 205, 'edit', NOW(), NOW()),   -- 岗位管理
(3, 206, 'edit', NOW(), NOW()),   -- 权限管理;

-- 步骤 3: 验证结果
SELECT '✅ Menu ID 重构完成！' AS message;

SELECT '📋 新的菜单ID体系：' AS info;
SELECT
    id AS menu_id,
    name AS menu_name,
    path,
    CASE
        WHEN id BETWEEN 1 AND 100 THEN '一级菜单'
        WHEN id BETWEEN 101 AND 299 THEN '二级菜单 (系统管理)'
        WHEN id BETWEEN 301 AND 399 THEN '二级菜单 (审计日志)'
        WHEN id BETWEEN 401 AND 499 THEN '二级菜单 (系统设置)'
        ELSE '其他'
    END AS menu_level,
    parent_id
FROM menus
ORDER BY id;

-- 步骤 4: 查看完整的权限映射
SELECT '🔑 权限映射 (admin 角色):' AS info;
SELECT
    m.id AS menu_id,
    m.name AS menu_name,
    m.path,
    p.permission_type
FROM permissions p
INNER JOIN menus m ON p.menu_id = m.id
WHERE p.role_id = 1
ORDER BY m.id;

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS temp_menus;

-- ====================================================================
-- 重构后的菜单ID体系说明
-- ====================================================================
--
-- 一级菜单 (1-100):
--   1 - 仪表盘 (/dashboard)
--   2 - 系统管理 (/system)
--   3 - 审计日志 (/audits)
--   4 - 系统设置 (/settings)
--
-- 二级菜单 (100-999):
--   系统管理模块 (201-299):
--     201 - 用户管理 (/system/users)
--     202 - 角色管理 (/system/roles)
--     203 - 菜单管理 (/system/menus)
--     204 - 部门管理 (/system/departments)
--     205 - 岗位管理 (/system/positions)
--     206 - 权限管理 (/system/permissions)
--
--   审计日志模块 (301-399):
--     301 - 操作日志 (/audits/operations)
--
--   系统设置模块 (401-499):
--     401 - Security (/settings/security)
-- ====================================================================
