-- 系统配置表创建脚本 - 如果表不存在则创建
-- 用于存储系统运行时配置信息，支持热更新配置
-- Create system_configs table if it doesn't exist
CREATE TABLE IF NOT EXISTS system_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    config_type VARCHAR(20) DEFAULT 'STRING',
    description VARCHAR(500),
    is_public BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 插入默认系统配置数据
-- 系统启动时自动插入这些默认配置，如果配置已存在则忽略（INSERT IGNORE）
-- Insert default system configurations
INSERT IGNORE INTO system_configs (config_key, config_value, config_type, description, is_public) VALUES
('system.name', 'DevOps Admin Platform', 'STRING', '系统名称', TRUE),
('system.maintenance_mode', 'false', 'BOOLEAN', '维护模式', FALSE),
('system.language', 'English', 'STRING', '系统语言', TRUE),
('system.log_level', 'Info', 'STRING', '日志级别', FALSE),
('security.password.complexity', 'Medium', 'STRING', '密码复杂度', FALSE),
('security.password.min_length', '8', 'NUMBER', '密码最小长度', TRUE),
('security.password.expiration_days', '90', 'NUMBER', '密码过期天数', FALSE),
('security.login.max_attempts', '5', 'NUMBER', '最大登录尝试次数', FALSE),
('security.login.lock_duration', '30', 'NUMBER', '账户锁定时长(分钟)', FALSE),
('security.login.two_factor_auth', 'false', 'BOOLEAN', '登录双因子认证', FALSE),
('security.user.two_factor_auth', 'false', 'BOOLEAN', '用户双因子认证', FALSE),
('security.user.login_notifications', 'true', 'BOOLEAN', '登录通知', FALSE),
('security.user.session_timeout', 'true', 'BOOLEAN', '会话超时', FALSE),
('security.user.data_encryption', 'true', 'BOOLEAN', '数据加密', FALSE),
('security.ip.allowed_ips', '', 'STRING', '允许的IP地址', FALSE),
('security.ip.blocked_ips', '', 'STRING', '阻止的IP地址', FALSE);