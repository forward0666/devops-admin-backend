CREATE TABLE IF NOT EXISTS bot_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bot_name VARCHAR(100) NOT NULL,
    bot_config_id BIGINT,
    chat_id BIGINT NOT NULL,
    chat_title VARCHAR(255),
    chat_type VARCHAR(50) DEFAULT 'supergroup',
    project_id BIGINT,
    project_name VARCHAR(255),
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_bot_chat (bot_name, chat_id)
);

CREATE TABLE IF NOT EXISTS bot_group_topic (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bot_name VARCHAR(100) NOT NULL,
    chat_id BIGINT NOT NULL,
    thread_id BIGINT,
    topic_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_bot_chat (bot_name, chat_id)
);
