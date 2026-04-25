package com.seedcrm.crm.scheduler.config;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SchedulerIntegrationSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public SchedulerIntegrationSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        ensureTable("integration_provider_config", createProviderSql(), providerColumns());
        ensureTable("integration_callback_config", createCallbackSql(), callbackColumns());
        ensureTable("integration_callback_event_log", createCallbackEventLogSql(), callbackEventLogColumns());
        ensureSchedulerJobProviderColumn();
        seedDefaultProvider();
        bindDefaultSchedulerJob();
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

    private String createProviderSql() {
        return """
                CREATE TABLE integration_provider_config (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    provider_code VARCHAR(64) NOT NULL,
                    provider_name VARCHAR(128) NOT NULL,
                    module_code VARCHAR(64) NOT NULL,
                    execution_mode VARCHAR(16) DEFAULT 'MOCK',
                    auth_type VARCHAR(64),
                    app_id VARCHAR(128),
                    base_url VARCHAR(255),
                    token_url VARCHAR(255),
                    endpoint_path VARCHAR(255),
                    client_key VARCHAR(128),
                    client_secret VARCHAR(255),
                    redirect_uri VARCHAR(255),
                    scope VARCHAR(255),
                    auth_code VARCHAR(255),
                    auth_code_status VARCHAR(32),
                    access_token VARCHAR(255),
                    refresh_token VARCHAR(255),
                    token_expires_at DATETIME,
                    refresh_token_expires_at DATETIME,
                    last_refresh_at DATETIME,
                    account_id VARCHAR(128),
                    life_account_ids VARCHAR(255),
                    local_account_ids VARCHAR(255),
                    open_id VARCHAR(128),
                    page_size INT DEFAULT 20,
                    pull_window_minutes INT DEFAULT 60,
                    overlap_minutes INT DEFAULT 10,
                    request_timeout_ms INT DEFAULT 10000,
                    callback_url VARCHAR(255),
                    enabled TINYINT DEFAULT 1,
                    remark VARCHAR(255),
                    last_test_status VARCHAR(32),
                    last_test_message VARCHAR(255),
                    auth_status VARCHAR(32),
                    last_callback_status VARCHAR(32),
                    last_callback_message VARCHAR(255),
                    last_test_at DATETIME,
                    last_auth_code_at DATETIME,
                    last_callback_at DATETIME,
                    last_callback_payload TEXT,
                    last_sync_time DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_integration_provider_code (provider_code)
                )
                """;
    }

    private String createCallbackSql() {
        return """
                CREATE TABLE integration_callback_config (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    provider_code VARCHAR(64),
                    callback_name VARCHAR(128) NOT NULL,
                    callback_url VARCHAR(255) NOT NULL,
                    signature_mode VARCHAR(64),
                    token_value VARCHAR(255),
                    aes_key VARCHAR(255),
                    enabled TINYINT DEFAULT 1,
                    remark VARCHAR(255),
                    last_check_status VARCHAR(32),
                    last_check_message VARCHAR(255),
                    last_callback_status VARCHAR(32),
                    last_callback_message VARCHAR(255),
                    last_callback_at DATETIME,
                    last_trace_id VARCHAR(64),
                    last_auth_code VARCHAR(255),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_integration_callback_name (callback_name)
                )
                """;
    }

    private String createCallbackEventLogSql() {
        return """
                CREATE TABLE integration_callback_event_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    provider_code VARCHAR(64),
                    callback_name VARCHAR(128),
                    app_code VARCHAR(64),
                    request_method VARCHAR(16),
                    callback_path VARCHAR(255),
                    query_string TEXT,
                    request_payload TEXT,
                    auth_code VARCHAR(255),
                    callback_state VARCHAR(255),
                    event_type VARCHAR(128),
                    trace_id VARCHAR(64),
                    signature_status VARCHAR(32),
                    process_status VARCHAR(32),
                    process_message VARCHAR(255),
                    received_at DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_callback_provider_received (provider_code, received_at),
                    KEY idx_callback_trace_id (trace_id)
                )
                """;
    }

    private Map<String, String> providerColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("provider_code", "provider_code VARCHAR(64) NOT NULL");
        columns.put("provider_name", "provider_name VARCHAR(128) NOT NULL");
        columns.put("module_code", "module_code VARCHAR(64) NOT NULL");
        columns.put("execution_mode", "execution_mode VARCHAR(16) DEFAULT 'MOCK'");
        columns.put("auth_type", "auth_type VARCHAR(64)");
        columns.put("app_id", "app_id VARCHAR(128)");
        columns.put("base_url", "base_url VARCHAR(255)");
        columns.put("token_url", "token_url VARCHAR(255)");
        columns.put("endpoint_path", "endpoint_path VARCHAR(255)");
        columns.put("client_key", "client_key VARCHAR(128)");
        columns.put("client_secret", "client_secret VARCHAR(255)");
        columns.put("redirect_uri", "redirect_uri VARCHAR(255)");
        columns.put("scope", "scope VARCHAR(255)");
        columns.put("auth_code", "auth_code VARCHAR(255)");
        columns.put("auth_code_status", "auth_code_status VARCHAR(32)");
        columns.put("access_token", "access_token VARCHAR(255)");
        columns.put("refresh_token", "refresh_token VARCHAR(255)");
        columns.put("token_expires_at", "token_expires_at DATETIME");
        columns.put("refresh_token_expires_at", "refresh_token_expires_at DATETIME");
        columns.put("last_refresh_at", "last_refresh_at DATETIME");
        columns.put("account_id", "account_id VARCHAR(128)");
        columns.put("life_account_ids", "life_account_ids VARCHAR(255)");
        columns.put("local_account_ids", "local_account_ids VARCHAR(255)");
        columns.put("open_id", "open_id VARCHAR(128)");
        columns.put("page_size", "page_size INT DEFAULT 20");
        columns.put("pull_window_minutes", "pull_window_minutes INT DEFAULT 60");
        columns.put("overlap_minutes", "overlap_minutes INT DEFAULT 10");
        columns.put("request_timeout_ms", "request_timeout_ms INT DEFAULT 10000");
        columns.put("callback_url", "callback_url VARCHAR(255)");
        columns.put("enabled", "enabled TINYINT DEFAULT 1");
        columns.put("remark", "remark VARCHAR(255)");
        columns.put("last_test_status", "last_test_status VARCHAR(32)");
        columns.put("last_test_message", "last_test_message VARCHAR(255)");
        columns.put("auth_status", "auth_status VARCHAR(32)");
        columns.put("last_callback_status", "last_callback_status VARCHAR(32)");
        columns.put("last_callback_message", "last_callback_message VARCHAR(255)");
        columns.put("last_test_at", "last_test_at DATETIME");
        columns.put("last_auth_code_at", "last_auth_code_at DATETIME");
        columns.put("last_callback_at", "last_callback_at DATETIME");
        columns.put("last_callback_payload", "last_callback_payload TEXT");
        columns.put("last_sync_time", "last_sync_time DATETIME");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }

    private Map<String, String> callbackColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("provider_code", "provider_code VARCHAR(64)");
        columns.put("callback_name", "callback_name VARCHAR(128) NOT NULL");
        columns.put("callback_url", "callback_url VARCHAR(255) NOT NULL");
        columns.put("signature_mode", "signature_mode VARCHAR(64)");
        columns.put("token_value", "token_value VARCHAR(255)");
        columns.put("aes_key", "aes_key VARCHAR(255)");
        columns.put("enabled", "enabled TINYINT DEFAULT 1");
        columns.put("remark", "remark VARCHAR(255)");
        columns.put("last_check_status", "last_check_status VARCHAR(32)");
        columns.put("last_check_message", "last_check_message VARCHAR(255)");
        columns.put("last_callback_status", "last_callback_status VARCHAR(32)");
        columns.put("last_callback_message", "last_callback_message VARCHAR(255)");
        columns.put("last_callback_at", "last_callback_at DATETIME");
        columns.put("last_trace_id", "last_trace_id VARCHAR(64)");
        columns.put("last_auth_code", "last_auth_code VARCHAR(255)");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }

    private Map<String, String> callbackEventLogColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("provider_code", "provider_code VARCHAR(64)");
        columns.put("callback_name", "callback_name VARCHAR(128)");
        columns.put("app_code", "app_code VARCHAR(64)");
        columns.put("request_method", "request_method VARCHAR(16)");
        columns.put("callback_path", "callback_path VARCHAR(255)");
        columns.put("query_string", "query_string TEXT");
        columns.put("request_payload", "request_payload TEXT");
        columns.put("auth_code", "auth_code VARCHAR(255)");
        columns.put("callback_state", "callback_state VARCHAR(255)");
        columns.put("event_type", "event_type VARCHAR(128)");
        columns.put("trace_id", "trace_id VARCHAR(64)");
        columns.put("signature_status", "signature_status VARCHAR(32)");
        columns.put("process_status", "process_status VARCHAR(32)");
        columns.put("process_message", "process_message VARCHAR(255)");
        columns.put("received_at", "received_at DATETIME");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private void seedDefaultProvider() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM integration_provider_config
                WHERE provider_code = 'DOUYIN_LAIKE'
                """, Integer.class);
        if (count != null && count > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO integration_provider_config(
                    provider_code, provider_name, module_code, execution_mode, auth_type,
                    app_id, base_url, token_url, endpoint_path, page_size, pull_window_minutes,
                    overlap_minutes, request_timeout_ms,
                    enabled, remark, auth_status, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "DOUYIN_LAIKE", "抖音来客线索", "CLUE", "MOCK", "AUTH_CODE",
                null,
                "https://api.oceanengine.com", "https://api.oceanengine.com/open_api/oauth2/access_token/",
                "/open_api/2/tools/clue/life/get/", 20, 60, 10, 10000,
                1, "默认保留 MOCK 模式，便于本地联调", "MOCK", now, now);
    }

    private void ensureSchedulerJobProviderColumn() {
        if (!tableExists("scheduler_job")) {
            return;
        }
        if (!columnExists("scheduler_job", "provider_id")) {
            jdbcTemplate.execute("ALTER TABLE scheduler_job ADD COLUMN provider_id BIGINT");
            log.info("added missing column scheduler_job.provider_id");
        }
    }

    private void bindDefaultSchedulerJob() {
        Long providerId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM integration_provider_config
                WHERE provider_code = 'DOUYIN_LAIKE'
                LIMIT 1
                """, Long.class);
        if (providerId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE scheduler_job
                SET provider_id = ?
                WHERE job_code = 'DOUYIN_CLUE_INCREMENTAL'
                  AND (provider_id IS NULL OR provider_id = 0)
                """, providerId);
    }
}
