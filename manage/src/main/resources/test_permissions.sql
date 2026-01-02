-- 测试查询：验证 role_id=1 (admin) 的权限
SELECT
    r.id AS role_id,
    r.code AS role_code,
    r.name AS role_name,
    p.menu_id,
    m.directory_id,
    m.name AS menu_name,
    p.permission_type
FROM permissions p
LEFT JOIN roles r ON p.role_id = r.id
LEFT JOIN menus m ON p.menu_id = m.id
WHERE r.code = 'admin'
ORDER BY m.directory_id;

-- 验证结果应该显示：
-- role_id=1, role_code='admin'
-- menu_id: 3 -> directory_id: 201 (用户管理)
-- menu_id: 4 -> directory_id: 202 (角色管理)
-- menu_id: 5 -> directory_id: 203 (菜单管理)
-- menu_id: 6 -> directory_id: 204 (部门管理)
-- menu_id: 7 -> directory_id: 205 (岗位管理)
-- menu_id: 11 -> directory_id: 206 (权限管理)
