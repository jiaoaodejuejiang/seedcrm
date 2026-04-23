CREATE TABLE IF NOT EXISTS clue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(32),
    wechat VARCHAR(64),
    name VARCHAR(128),
    source VARCHAR(64),
    source_channel VARCHAR(32),
    source_id BIGINT,
    raw_data LONGTEXT,
    status VARCHAR(32),
    current_owner_id BIGINT,
    is_public TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_clue_phone (phone),
    UNIQUE KEY uk_clue_wechat (wechat)
);
