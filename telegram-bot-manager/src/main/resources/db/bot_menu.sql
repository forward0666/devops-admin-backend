-- Bot Menu 表（MySQL，用于 telegram-bot-manager）
CREATE TABLE IF NOT EXISTS bot_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bot_name VARCHAR(100) NOT NULL COMMENT 'Bot 名称',
    menu_level INT NOT NULL DEFAULT 1 COMMENT '菜单层级: 1=主菜单, 2=子菜单',
    menu_key VARCHAR(100) NOT NULL COMMENT '唯一标识，用于 callback_data 匹配',
    title VARCHAR(200) DEFAULT NULL COMMENT '显示标题',
    buttons JSON DEFAULT NULL COMMENT '按钮JSON数组: [{\"text\":\"xxx\",\"callbackData\":\"xxx\"}]',
    parent_id BIGINT DEFAULT NULL COMMENT '上级菜单ID，主菜单为null',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bot_name (bot_name),
    INDEX idx_bot_name_level (bot_name, menu_level),
    UNIQUE INDEX uk_bot_menu_key (bot_name, menu_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Telegram Bot 菜单配置';
