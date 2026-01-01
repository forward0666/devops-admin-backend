-- Add permission_type column to role_menu_permissions table
-- 为 role_menu_permissions 表添加 permission_type 字段

-- Add permission_type column
ALTER TABLE `role_menu_permissions`
ADD COLUMN `permission_type` VARCHAR(20) NOT NULL DEFAULT 'view' COMMENT '权限类型: view-查看, edit-编辑, all-全部' AFTER `menu_id`;

-- Add index for permission_type
ALTER TABLE `role_menu_permissions`
ADD INDEX `idx_permission_type` (`permission_type`) COMMENT '权限类型索引';

-- Update existing records to have permission_type (default is 'view')
UPDATE `role_menu_permissions` SET `permission_type` = 'view' WHERE `permission_type` IS NULL;
