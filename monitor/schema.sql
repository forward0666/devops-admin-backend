-- Monitor rules table
CREATE DATABASE IF NOT EXISTS monitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE monitor;

CREATE TABLE IF NOT EXISTS monitor_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'cloudflare' COMMENT 'cloudflare|tencent|custom',
    account_id BIGINT DEFAULT NULL COMMENT 'CF account id, null for all',
    domains JSON DEFAULT NULL COMMENT 'selected domain names, ["all"] for all',
    custom_domains TEXT DEFAULT NULL COMMENT 'custom domains, one per line',
    check_interval INT NOT NULL DEFAULT 5 COMMENT 'check interval in minutes',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    status VARCHAR(50) DEFAULT 'pending' COMMENT 'pending|running|error',
    last_check DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_enabled (enabled),
    INDEX idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
