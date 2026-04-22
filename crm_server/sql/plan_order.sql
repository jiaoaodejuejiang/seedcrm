CREATE TABLE IF NOT EXISTS plan_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    arrive_time DATETIME,
    start_time DATETIME,
    finish_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plan_order_order_id (order_id)
);
