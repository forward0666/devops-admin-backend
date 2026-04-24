CREATE TABLE IF NOT EXISTS projects (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  type VARCHAR(100),
  status VARCHAR(50) DEFAULT 'active',
  progress INT DEFAULT 0,
  leader VARCHAR(200),
  department_id BIGINT,
  description TEXT,
  tech_stack VARCHAR(500),
  objectives TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_by BIGINT,
  active BOOLEAN DEFAULT TRUE,
  UNIQUE KEY uk_project_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
