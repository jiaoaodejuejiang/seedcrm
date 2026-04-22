CREATE TABLE IF NOT EXISTS distributor (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    contact_info VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS distributor_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    distributor_id BIGINT NOT NULL,
    rule_type VARCHAR(16) NOT NULL,
    rule_value DECIMAL(10,4) NOT NULL,
    is_active TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_distributor_rule_distributor_active (distributor_id, is_active)
);

CREATE TABLE IF NOT EXISTS distributor_income_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    distributor_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_amount DECIMAL(12,2) NOT NULL,
    income_amount DECIMAL(12,2) NOT NULL,
    settlement_id BIGINT,
    settlement_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_distributor_income_detail_order_id (order_id),
    KEY idx_distributor_income_detail_distributor_id (distributor_id),
    KEY idx_distributor_income_detail_settlement_id (settlement_id)
);

CREATE TABLE IF NOT EXISTS distributor_settlement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    distributor_id BIGINT NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_distributor_settlement_distributor_id (distributor_id),
    KEY idx_distributor_settlement_status (status)
);

CREATE TABLE IF NOT EXISTS distributor_withdraw (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    distributor_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_distributor_withdraw_distributor_id (distributor_id),
    KEY idx_distributor_withdraw_status (status)
);

ALTER TABLE clue ADD COLUMN source_channel VARCHAR(32);
ALTER TABLE clue ADD COLUMN source_id BIGINT;

ALTER TABLE customer ADD COLUMN source_channel VARCHAR(32);
ALTER TABLE customer ADD COLUMN source_id BIGINT;

ALTER TABLE order_info ADD COLUMN source_channel VARCHAR(32);
ALTER TABLE order_info ADD COLUMN source_id BIGINT;
