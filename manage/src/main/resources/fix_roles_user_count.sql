-- ========================================
-- 检查并修复 roles 表的 user_count 列
-- ========================================

-- 检查 user_count 为 NULL 的角色
SELECT '检查 user_count 为 NULL 的角色' AS message;
SELECT id, name, code, user_count, status
FROM roles
WHERE user_count IS NULL OR user_count < 0
ORDER BY id;

-- 修复 user_count 为 NULL 的记录
UPDATE roles
SET user_count = 0
WHERE user_count IS NULL OR user_count < 0;

-- 统计每个角色的实际用户数量
SELECT '统计实际用户数量' AS message;
SELECT
    r.id,
    r.name,
    r.code,
    r.user_count AS current_user_count,
    (SELECT COUNT(*) FROM users WHERE role = r.code AND active = 1) AS actual_user_count
FROM roles r
ORDER BY r.id;

-- 更新角色表中的用户数量
UPDATE roles r
SET user_count = (
    SELECT COUNT(*)
    FROM users
    WHERE role = r.code AND active = 1
)
WHERE r.deleted_at IS NULL;

-- 验证修复结果
SELECT '修复后的角色数据' AS message;
SELECT id, name, code, user_count, status
FROM roles
WHERE deleted_at IS NULL
ORDER BY id;
