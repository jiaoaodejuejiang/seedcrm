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
  source VARCHAR(64) COMMENT '来源：douyin',
  status VARCHAR(32) COMMENT 'new/assigned/following/converted',
  current_owner_id BIGINT COMMENT '当前跟进人',
  is_public TINYINT DEFAULT 1 COMMENT '是否在公海',
  created_at DATETIME,
  updated_at DATETIME,
  UNIQUE KEY uk_phone (phone),
  UNIQUE KEY uk_wechat (wechat)
);
