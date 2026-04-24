CREATE TABLE IF NOT EXISTS project_members (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  username VARCHAR(100),
  full_name VARCHAR(200),
  role VARCHAR(100) DEFAULT 'Developer',
  position VARCHAR(100),
  status VARCHAR(50) DEFAULT 'active',
  joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_by BIGINT,
  active BOOLEAN DEFAULT TRUE,
  UNIQUE KEY uk_project_user (project_id, user_id),
  KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
