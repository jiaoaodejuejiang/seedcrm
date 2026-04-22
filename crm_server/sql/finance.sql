CREATE TABLE IF NOT EXISTS account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_type VARCHAR(32) NOT NULL,
    owner_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_owner (owner_type, owner_id)
);

CREATE TABLE IF NOT EXISTS ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    change_amount DECIMAL(12,2) NOT NULL,
    balance_after DECIMAL(12,2),
    biz_type VARCHAR(32) NOT NULL,
    biz_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ledger_biz_account (biz_type, biz_id, account_id),
    KEY idx_ledger_biz_type_biz_id (biz_type, biz_id),
    KEY idx_ledger_account_id (account_id)
);

CREATE TABLE IF NOT EXISTS finance_check_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    biz_type VARCHAR(32) NOT NULL,
    biz_id BIGINT NOT NULL,
    expected_amount DECIMAL(12,2) NOT NULL,
    actual_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_finance_check_record_biz (biz_type, biz_id)
);

DELIMITER $$
CREATE TRIGGER trg_ledger_before_update
BEFORE UPDATE ON ledger
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger is immutable';
END$$

CREATE TRIGGER trg_ledger_before_delete
BEFORE DELETE ON ledger
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger is immutable';
END$$
DELIMITER ;

CREATE TABLE IF NOT EXISTS idempotent_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    biz_key VARCHAR(128) NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotent_record_biz_key (biz_key),
    KEY idx_idempotent_record_biz_type_status (biz_type, status)
);
