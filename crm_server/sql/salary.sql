CREATE TABLE IF NOT EXISTS salary_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(32) NOT NULL,
    rule_type VARCHAR(16) NOT NULL,
    rule_value DECIMAL(10,4) NOT NULL,
    is_active TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_salary_rule_role_active (role_code, is_active)
);

CREATE TABLE IF NOT EXISTS salary_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    order_amount DECIMAL(12,2) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    settlement_id BIGINT,
    settlement_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_salary_detail_plan_user_role (plan_order_id, user_id, role_code),
    KEY idx_salary_detail_plan_order_id (plan_order_id),
    KEY idx_salary_detail_user_id (user_id),
    KEY idx_salary_detail_settlement_id (settlement_id)
);

CREATE TABLE IF NOT EXISTS salary_settlement (
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

CREATE TABLE IF NOT EXISTS withdraw_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_withdraw_record_user_id (user_id),
    KEY idx_withdraw_record_status (status)
);
