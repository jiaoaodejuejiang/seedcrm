CREATE TABLE IF NOT EXISTS customer (
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
