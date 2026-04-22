CREATE TABLE IF NOT EXISTS order_role_record (
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
