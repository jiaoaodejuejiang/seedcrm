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
  tag VARCHAR(64),
  first_order_time DATETIME,
  last_order_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_customer_phone (phone)
);

CREATE TABLE customer_tag_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tag_code VARCHAR(64),
  rule_type VARCHAR(32),
  rule_value VARCHAR(255),
  priority INT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE customer_wecom_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  external_userid VARCHAR(128) NOT NULL,
  wecom_user_id VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_customer_wecom_relation_customer_id (customer_id),
  KEY idx_customer_wecom_relation_external_userid (external_userid)
);

CREATE TABLE wecom_touch_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tag VARCHAR(64) NOT NULL,
  message_template TEXT,
  trigger_type VARCHAR(16) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_wecom_touch_rule_tag_trigger (tag, trigger_type)
);

CREATE TABLE wecom_touch_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  external_userid VARCHAR(128),
  message TEXT,
  status VARCHAR(16) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_wecom_touch_log_customer_id (customer_id),
  KEY idx_wecom_touch_log_external_userid (external_userid)
);
