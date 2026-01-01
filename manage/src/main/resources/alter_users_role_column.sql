-- 修改 users 表的 role 列长度
-- 从 VARCHAR(50) 增加到 VARCHAR(100) 以支持更多角色代码

ALTER TABLE users MODIFY COLUMN role VARCHAR(100) NOT NULL COMMENT '角色代码';
