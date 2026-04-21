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

CREATE TABLE clue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(32),
  wechat VARCHAR(64),
  name VARCHAR(128),
  source VARCHAR(64) COMMENT 'lead source',
  status VARCHAR(32) COMMENT 'new/assigned/following/converted',
  current_owner_id BIGINT COMMENT 'current owner',
  is_public TINYINT DEFAULT 1 COMMENT 'public clue flag',
  created_at DATETIME,
  updated_at DATETIME,
  UNIQUE KEY uk_clue_phone (phone),
  UNIQUE KEY uk_clue_wechat (wechat)
);

CREATE TABLE customer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128),
  phone VARCHAR(32) NOT NULL,
  wechat VARCHAR(64),
  source_clue_id BIGINT,
  status VARCHAR(32) NOT NULL,
  level VARCHAR(32),
  first_order_time DATETIME,
  last_order_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_customer_phone (phone)
);
