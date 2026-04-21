CREATE TABLE IF NOT EXISTS customer (
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

CREATE TABLE IF NOT EXISTS customer_tag_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tag_code VARCHAR(64),
    rule_type VARCHAR(32),
    rule_value VARCHAR(255),
    priority INT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customer_wecom_relation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    external_userid VARCHAR(128) NOT NULL,
    wecom_user_id VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_wecom_relation_customer_id (customer_id),
    KEY idx_customer_wecom_relation_external_userid (external_userid)
);

CREATE TABLE IF NOT EXISTS wecom_touch_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tag VARCHAR(64) NOT NULL,
    message_template TEXT,
    trigger_type VARCHAR(16) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_wecom_touch_rule_tag_trigger (tag, trigger_type)
);

CREATE TABLE IF NOT EXISTS wecom_touch_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    external_userid VARCHAR(128),
    message TEXT,
    status VARCHAR(16) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_wecom_touch_log_customer_id (customer_id),
    KEY idx_wecom_touch_log_external_userid (external_userid)
);
