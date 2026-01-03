-- Allow permission_type to be NULL (menu with no permission)
-- This supports the case where a menu is selected but has no permission type
ALTER TABLE permissions MODIFY COLUMN permission_type VARCHAR(20) NULL COMMENT 'Permission type (view- null, view- 查看权限, edit- 编辑权限, all- 全部权限)';
