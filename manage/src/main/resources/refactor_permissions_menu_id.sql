-- ====================================================================
-- 权限系统重构：统一使用 menu_id，去掉 directory_id
-- ====================================================================

-- 步骤 1: 更新 permissions 表，确保 menu_id 正确
-- 注意：当前数据已经是正确的，无需修改
-- role_id=1 (admin) 的权限数据：
-- menu_id: 1-12 对应所有菜单，已经是正确的

-- 步骤 2: 删除 menus 表的 directory_id 列
ALTER TABLE menus
DROP COLUMN IF EXISTS directory_id;

-- 验证修改
SELECT
    m.id AS menu_id,
    m.name AS menu_name,
    m.path,
    COUNT(p.role_id) AS permission_count
FROM menus m
LEFT JOIN permissions p ON m.id = p.menu_id
GROUP BY m.id, m.name, m.path
ORDER BY m.id;

-- 查看完整的权限映射
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
SELECT '权限系统重构完成！现在统一使用 menu_id 进行权限检查' AS message;
