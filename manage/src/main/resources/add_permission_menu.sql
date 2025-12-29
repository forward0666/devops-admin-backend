-- 添加权限管理菜单到系统
-- 执行此脚本以添加权限管理菜单项

INSERT INTO menus (name, path, icon, type, sort, status, parent_id)
VALUES ('权限管理', '/dashboard/system/permissions', 'i-heroicons-key', 'menu', 6, 'active', 2)
ON DUPLICATE KEY UPDATE
  name = '权限管理',
  path = '/dashboard/system/permissions',
  icon = 'i-heroicons-key',
  type = 'menu',
  sort = 6,
  status = 'active';
