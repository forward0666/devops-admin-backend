-- ====================================================================
-- 权限系统重构：移除 directory_id，统一使用 menu_id
-- ====================================================================

-- 步骤 1: 删除 menus 表的 directory_id 列
ALTER TABLE menus
DROP COLUMN IF EXISTS directory_id;

-- 步骤 2: 验证修改
SELECT
    m.id AS menu_id,
    m.name AS menu_name,
    m.path,
    COUNT(p.role_id) AS permission_count
FROM menus m
LEFT JOIN permissions p ON m.id = p.menu_id
GROUP BY m.id, m.name, m.path
ORDER BY m.id;

-- 步骤 3: 查看完整的权限映射
SELECT
    r.id AS role_id,
    r.code AS role_code,
    r.name AS role_name,
    m.id AS menu_id,
    m.name AS menu_name,
    m.path AS menu_path,
    p.permission_type
FROM permissions p
LEFT JOIN roles r ON p.role_id = r.id
LEFT JOIN menus m ON p.menu_id = m.id
ORDER BY r.id, m.id;

-- 输出重构完成信息
SELECT '✅ 权限系统重构完成！现在统一使用 menu_id 进行权限检查' AS message;
SELECT '📋 菜单权限映射表 (menu_id -> permission_type):' AS info;
SELECT
    m.id AS menu_id,
    m.name AS menu_name,
    m.path AS menu_path
FROM menus m
ORDER BY m.id;

-- ====================================================================
-- 重构总结
-- ====================================================================
-- 1. ✅ 数据库：移除了 menus.directory_id 列
-- 2. ✅ 后端：
--    - PermissionMappingEntity：移除了 directoryId 字段
--    - PermissionRequestDto：移除了 directoryId 和 directoryIds 字段
--    - PermissionResponseDto：移除了 directoryIds 字段
--    - PermissionService：移除了 directoryId 转换逻辑
--    - MenuMapper：移除了 findByDirectoryId 方法
--    - MenuEntity：移除了 directoryId 字段
-- 3. ✅ 前端：
--    - auth.global.ts：menuId 改回 3, 4, 5, 6, 7, 11
--    - dashboard.ts：menuId 改回 3, 4, 5, 6, 7, 11
--    - auth.ts：使用 menuIds 而不是 directoryIds
--    - 所有页面：menuId 改回 3, 4, 5, 6, 7, 11
-- 4. ✅ Composables：
--    - useMenuDirectory：currentDirectoryId -> currentMenuId
--    - usePagePermissions：currentDirectoryId -> currentMenuId
-- ====================================================================
