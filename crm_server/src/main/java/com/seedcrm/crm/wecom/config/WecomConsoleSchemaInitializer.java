package com.seedcrm.crm.wecom.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WecomConsoleSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public WecomConsoleSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        ensureTable("wecom_app_config", createAppConfigSql(), appConfigColumns());
        ensureTable("wecom_live_code_config", createLiveCodeSql(), liveCodeColumns());
    }

    private void ensureTable(String tableName, String createSql, Map<String, String> columns) {
        if (!tableExists(tableName)) {
            jdbcTemplate.execute(createSql);
            log.info("created table {}", tableName);
            return;
        }
        for (Map.Entry<String, String> entry : columns.entrySet()) {
            if (!columnExists(tableName, entry.getKey())) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + entry.getValue());
                log.info("added missing column {}.{}", tableName, entry.getKey());
            }
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private String createAppConfigSql() {
        return """
                CREATE TABLE wecom_app_config (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    app_code VARCHAR(64) NOT NULL,
                    app_id VARCHAR(128),
                    suite_id VARCHAR(128),
                    auth_mode VARCHAR(32),
                    corp_id VARCHAR(128),
                    auth_corp_id VARCHAR(128),
                    agent_id VARCHAR(64),
                    app_secret VARCHAR(255),
                    suite_secret VARCHAR(255),
                    auth_code VARCHAR(255),
                    suite_ticket VARCHAR(255),
                    permanent_code VARCHAR(255),
                    access_token VARCHAR(255),
                    refresh_token VARCHAR(255),
                    suite_access_token VARCHAR(255),
                    corp_access_token VARCHAR(255),
                    execution_mode VARCHAR(16) DEFAULT 'MOCK',
                    callback_url VARCHAR(255),
                    redirect_uri VARCHAR(255),
                    trusted_domain VARCHAR(255),
                    callback_token VARCHAR(255),
                    encoding_aes_key VARCHAR(255),
                    live_code_type INT DEFAULT 2,
                    live_code_scene INT DEFAULT 2,
                    live_code_style INT DEFAULT 1,
                    skip_verify TINYINT DEFAULT 1,
                    state_template VARCHAR(255),
                    mark_source VARCHAR(128),
                    enabled TINYINT DEFAULT 1,
                    last_token_status VARCHAR(32),
                    last_token_message VARCHAR(255),
                    auth_status VARCHAR(32),
                    last_callback_status VARCHAR(32),
                    last_callback_message VARCHAR(255),
                    last_token_checked_at DATETIME,
                    suite_ticket_at DATETIME,
                    last_auth_code_at DATETIME,
                    token_expires_at DATETIME,
                    suite_access_token_expires_at DATETIME,
                    corp_access_token_expires_at DATETIME,
                    last_callback_at DATETIME,
                    last_callback_payload TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_wecom_app_code (app_code)
                )
                """;
    }

    private String createLiveCodeSql() {
        return """
                CREATE TABLE wecom_live_code_config (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    code_name VARCHAR(128) NOT NULL,
                    scene VARCHAR(128),
                    strategy VARCHAR(32) DEFAULT 'ROUND_ROBIN',
                    employee_names_json TEXT,
                    employee_accounts_json TEXT,
                    store_names_json TEXT,
                    remark VARCHAR(255),
                    is_enabled TINYINT DEFAULT 1,
                    contact_way_id VARCHAR(128),
                    qr_code_url TEXT,
                    short_link VARCHAR(255),
                    published_at DATETIME,
                    generated_at DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_wecom_live_code_name (code_name)
                )
                """;
    }

    private Map<String, String> appConfigColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("app_code", "app_code VARCHAR(64) NOT NULL");
        columns.put("app_id", "app_id VARCHAR(128)");
        columns.put("suite_id", "suite_id VARCHAR(128)");
        columns.put("auth_mode", "auth_mode VARCHAR(32)");
        columns.put("corp_id", "corp_id VARCHAR(128)");
        columns.put("auth_corp_id", "auth_corp_id VARCHAR(128)");
        columns.put("agent_id", "agent_id VARCHAR(64)");
        columns.put("app_secret", "app_secret VARCHAR(255)");
        columns.put("suite_secret", "suite_secret VARCHAR(255)");
        columns.put("auth_code", "auth_code VARCHAR(255)");
        columns.put("suite_ticket", "suite_ticket VARCHAR(255)");
        columns.put("permanent_code", "permanent_code VARCHAR(255)");
        columns.put("access_token", "access_token VARCHAR(255)");
        columns.put("refresh_token", "refresh_token VARCHAR(255)");
        columns.put("suite_access_token", "suite_access_token VARCHAR(255)");
        columns.put("corp_access_token", "corp_access_token VARCHAR(255)");
        columns.put("execution_mode", "execution_mode VARCHAR(16) DEFAULT 'MOCK'");
        columns.put("callback_url", "callback_url VARCHAR(255)");
        columns.put("redirect_uri", "redirect_uri VARCHAR(255)");
        columns.put("trusted_domain", "trusted_domain VARCHAR(255)");
        columns.put("callback_token", "callback_token VARCHAR(255)");
        columns.put("encoding_aes_key", "encoding_aes_key VARCHAR(255)");
        columns.put("live_code_type", "live_code_type INT DEFAULT 2");
        columns.put("live_code_scene", "live_code_scene INT DEFAULT 2");
        columns.put("live_code_style", "live_code_style INT DEFAULT 1");
        columns.put("skip_verify", "skip_verify TINYINT DEFAULT 1");
        columns.put("state_template", "state_template VARCHAR(255)");
        columns.put("mark_source", "mark_source VARCHAR(128)");
        columns.put("enabled", "enabled TINYINT DEFAULT 1");
        columns.put("last_token_status", "last_token_status VARCHAR(32)");
        columns.put("last_token_message", "last_token_message VARCHAR(255)");
        columns.put("auth_status", "auth_status VARCHAR(32)");
        columns.put("last_callback_status", "last_callback_status VARCHAR(32)");
        columns.put("last_callback_message", "last_callback_message VARCHAR(255)");
        columns.put("last_token_checked_at", "last_token_checked_at DATETIME");
        columns.put("suite_ticket_at", "suite_ticket_at DATETIME");
        columns.put("last_auth_code_at", "last_auth_code_at DATETIME");
        columns.put("token_expires_at", "token_expires_at DATETIME");
        columns.put("suite_access_token_expires_at", "suite_access_token_expires_at DATETIME");
        columns.put("corp_access_token_expires_at", "corp_access_token_expires_at DATETIME");
        columns.put("last_callback_at", "last_callback_at DATETIME");
        columns.put("last_callback_payload", "last_callback_payload TEXT");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }

    private Map<String, String> liveCodeColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("code_name", "code_name VARCHAR(128) NOT NULL");
        columns.put("scene", "scene VARCHAR(128)");
        columns.put("strategy", "strategy VARCHAR(32) DEFAULT 'ROUND_ROBIN'");
        columns.put("employee_names_json", "employee_names_json TEXT");
        columns.put("employee_accounts_json", "employee_accounts_json TEXT");
        columns.put("store_names_json", "store_names_json TEXT");
        columns.put("remark", "remark VARCHAR(255)");
        columns.put("is_enabled", "is_enabled TINYINT DEFAULT 1");
        columns.put("contact_way_id", "contact_way_id VARCHAR(128)");
        columns.put("qr_code_url", "qr_code_url TEXT");
        columns.put("short_link", "short_link VARCHAR(255)");
        columns.put("published_at", "published_at DATETIME");
        columns.put("generated_at", "generated_at DATETIME");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }
}
