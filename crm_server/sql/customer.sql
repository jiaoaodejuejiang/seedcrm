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
