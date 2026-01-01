-- ========================================
-- 删除 users 表的 role 字段 CHECK 约束
-- users.role 字段将由 roles 表管理，不再使用硬编码约束
-- ========================================

-- 删除 users_chk_1 CHECK 约束
ALTER TABLE users DROP CONSTRAINT users_chk_1;

-- 验证约束已删除
SELECT 
    'users_chk_1 约束已删除' AS message,
    CASE 
        WHEN NOT EXISTS (
            SELECT 1 
            FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS 
            WHERE CONSTRAINT_SCHEMA = DATABASE() 
            AND CONSTRAINT_NAME = 'users_chk_1'
        ) 
        THEN 'SUCCESS'
        ELSE 'FAILED'
    END AS status;

-- 显示当前 users 表的所有约束
SELECT 
    CONSTRAINT_NAME,
    CONSTRAINT_TYPE
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
ORDER BY CONSTRAINT_TYPE, CONSTRAINT_NAME;
