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
  source_channel VARCHAR(32) COMMENT 'DOUYIN / DISTRIBUTOR / OTHER',
  source_id BIGINT COMMENT 'channel or distributor id',
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
  source_channel VARCHAR(32),
  source_id BIGINT,
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

CREATE TABLE customer_tag_detail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  tag_code VARCHAR(64) NOT NULL,
  tag_name VARCHAR(128),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_customer_tag_detail_customer_id (customer_id)
);

CREATE TABLE customer_ecom_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  platform VARCHAR(64) NOT NULL,
  ecom_user_id VARCHAR(128) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_customer_ecom_user_platform (customer_id, platform, ecom_user_id)
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

CREATE TABLE distributor (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  contact_info VARCHAR(255),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  clue_id BIGINT,
  customer_id BIGINT,
  source_channel VARCHAR(32),
  source_id BIGINT,
  type INT NOT NULL,
  amount DECIMAL(10,2),
  deposit DECIMAL(10,2),
  status VARCHAR(32) NOT NULL,
  appointment_time DATETIME,
  arrive_time DATETIME,
  complete_time DATETIME,
  remark VARCHAR(255),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no)
);

CREATE TABLE plan_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  arrive_time DATETIME,
  start_time DATETIME,
  finish_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_plan_order_order_id (order_id)
);

CREATE TABLE order_role_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_order_id BIGINT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME,
  is_current TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_order_role_record_plan_order_id (plan_order_id),
  KEY idx_order_role_record_user_id (user_id),
    KEY idx_order_role_record_plan_role_current (plan_order_id, role_code, is_current)
);

CREATE TABLE salary_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(32) NOT NULL,
    rule_type VARCHAR(16) NOT NULL,
    rule_value DECIMAL(10,4) NOT NULL,
    is_active TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_salary_rule_role_active (role_code, is_active)
);

CREATE TABLE salary_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    order_amount DECIMAL(12,2) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    settlement_id BIGINT,
    settlement_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_salary_detail_plan_order_id (plan_order_id),
    KEY idx_salary_detail_user_id (user_id),
    KEY idx_salary_detail_settlement_id (settlement_id)
);

CREATE TABLE salary_settlement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_salary_settlement_user_id (user_id),
    KEY idx_salary_settlement_status (status)
);

CREATE TABLE withdraw_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_withdraw_record_user_id (user_id),
    KEY idx_withdraw_record_status (status)
);
