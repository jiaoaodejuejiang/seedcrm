CREATE TABLE IF NOT EXISTS order_info (
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
