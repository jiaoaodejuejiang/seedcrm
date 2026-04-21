CREATE TABLE role_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(32) NOT NULL,
  role_name VARCHAR(128) NOT NULL,
  role_type VARCHAR(32),
  sort INT DEFAULT 0,
  is_enabled TINYINT DEFAULT 1,
  created_at DATETIME,
  updated_at DATETIME
);
