-- ====================================================================
-- 角色表创建脚本（完整版）
-- 用于存储系统中的角色信息
-- ====================================================================

DROP TABLE IF EXISTS roles;

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    name VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
    code VARCHAR(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色代码（唯一标识）',
    description TEXT COLLATE utf8mb4_unicode_ci COMMENT '角色描述',
    status ENUM('active', 'inactive') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态：active-启用，inactive-禁用',
    user_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '使用该角色的用户数量',
    created_by BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
    updated_by BIGINT UNSIGNED DEFAULT NULL COMMENT '更新人ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '删除时间（软删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_status (status),
    KEY idx_created_at (created_at),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ====================================================================
-- 插入默认角色数据
-- ====================================================================

TRUNCATE TABLE roles;

INSERT INTO roles (id, name, code, description, status, user_count) VALUES
(1, 'Administrator', 'admin', '超级管理员，拥有系统所有权限', 'active', 0),
(2, 'DevOps Admin', 'devops_admin', 'DevOps管理员，负责运维和部署相关操作', 'active', 0),
(3, 'System Admin', 'sys_admin', '系统管理员，负责系统配置和监控', 'active', 0),
(4, 'User Admin', 'user_admin', '用户管理员，负责用户和权限管理', 'active', 0),
(5, 'Editor', 'editor', '编辑员，可以编辑和创建内容', 'active', 0),
(6, 'Viewer', 'viewer', '查看员，只读权限', 'active', 0);

-- ====================================================================
-- 说明
-- ====================================================================
-- 旧的 role_permissions 表已废弃
-- 现在使用 role_menu_permissions 表管理角色与菜单的权限映射
-- 权限管理功能请通过 PermissionService 和 PermissionController 实现

-- ====================================================================
-- 验证数据
-- ====================================================================

-- 查看所有角色
SELECT '角色数据插入完成' AS message, COUNT(*) AS total_count FROM roles;

-- 查看所有角色
SELECT id, code, name, status, user_count FROM roles ORDER BY id;

