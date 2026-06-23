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
    description VARCHAR(500) DEFAULT NULL COMMENT 'rule description',
    check_interval INT NOT NULL DEFAULT 5 COMMENT 'check interval in minutes',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    status VARCHAR(50) DEFAULT 'pending' COMMENT 'pending|running|error',
    last_check DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_enabled (enabled),
    INDEX idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ALTER TABLE monitor_rule ADD COLUMN description VARCHAR(500) DEFAULT NULL COMMENT 'rule description' AFTER custom_domains;

-- Sync Domain rules table
CREATE TABLE IF NOT EXISTS sync_domain_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    group_id VARCHAR(255) NOT NULL COMMENT 'domain group id',
    project_id VARCHAR(255) NOT NULL COMMENT 'target project id',
    env VARCHAR(50) NOT NULL DEFAULT 'prod' COMMENT 'prod|uat|test|dev',
    type VARCHAR(50) NOT NULL DEFAULT 'web' COMMENT 'web|admin|callback|api',
    description VARCHAR(500) DEFAULT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    status VARCHAR(50) DEFAULT 'pending' COMMENT 'pending|ok|error',
    last_check DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_enabled (enabled),
    INDEX idx_group_id (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
