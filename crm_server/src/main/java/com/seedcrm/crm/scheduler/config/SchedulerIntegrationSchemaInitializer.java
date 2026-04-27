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

    private static final String DOUYIN_PROVIDER_CODE = "DOUYIN_LAIKE";
    private static final String DOUYIN_PROVIDER_NAME = "抖音来客";
    private static final String DOUYIN_BASE_URL = "https://open.douyin.com";
    private static final String DOUYIN_TOKEN_URL = "https://open.douyin.com/oauth/access_token/";
    private static final String DOUYIN_CLUE_ENDPOINT = "/goodlife/v1/clue/douyin/list/";
    private static final String DOUYIN_VOUCHER_PREPARE_PATH = "/goodlife/v1/fulfilment/certificate/prepare/";
    private static final String DOUYIN_VOUCHER_VERIFY_PATH = "/goodlife/v1/fulfilment/certificate/verify/";
    private static final String DOUYIN_VOUCHER_CANCEL_PATH = "/goodlife/v1/fulfilment/certificate/cancel/";
    private static final String DOUYIN_REFUND_APPLY_PATH = "/api/apps/trade/v2/refund/create_refund";
    private static final String DOUYIN_REFUND_QUERY_PATH = "/api/apps/trade/v2/refund/query_refund";
    private static final String DOUYIN_REFUND_LIST_PATH = "/api/apps/trade/v2/refund/refund_list";
    private static final String DOUYIN_REFUND_NOTIFY_PATH = "/scheduler/callback/douyin/refund";
    private static final String DOUYIN_REFUND_AUDIT_CALLBACK_PATH = "/scheduler/callback/douyin/refund-audit";
    private static final String DOUYIN_REFUND_AMOUNT_UNIT = "CENT";
    private static final String DOUYIN_VERIFY_CODE_FIELD = "encrypted_codes";

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
                    voucher_prepare_path VARCHAR(255),
                    voucher_verify_path VARCHAR(255),
                    voucher_cancel_path VARCHAR(255),
                    refund_apply_path VARCHAR(255),
                    refund_query_path VARCHAR(255),
                    refund_list_path VARCHAR(255),
                    refund_notify_path VARCHAR(255),
                    refund_audit_callback_path VARCHAR(255),
                    refund_order_id_field VARCHAR(64),
                    refund_amount_field VARCHAR(64),
                    refund_reason_field VARCHAR(64),
                    refund_out_order_no_field VARCHAR(64),
                    refund_out_refund_no_field VARCHAR(64),
                    refund_external_refund_id_field VARCHAR(64),
                    refund_item_order_id_field VARCHAR(64),
                    refund_notify_url_field VARCHAR(64),
                    refund_amount_unit VARCHAR(16),
                    refund_status_mapping VARCHAR(255),
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
                    poi_id VARCHAR(128),
                    verify_code_field VARCHAR(64),
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
        columns.put("voucher_prepare_path", "voucher_prepare_path VARCHAR(255)");
        columns.put("voucher_verify_path", "voucher_verify_path VARCHAR(255)");
        columns.put("voucher_cancel_path", "voucher_cancel_path VARCHAR(255)");
        columns.put("refund_apply_path", "refund_apply_path VARCHAR(255)");
        columns.put("refund_query_path", "refund_query_path VARCHAR(255)");
        columns.put("refund_list_path", "refund_list_path VARCHAR(255)");
        columns.put("refund_notify_path", "refund_notify_path VARCHAR(255)");
        columns.put("refund_audit_callback_path", "refund_audit_callback_path VARCHAR(255)");
        columns.put("refund_order_id_field", "refund_order_id_field VARCHAR(64)");
        columns.put("refund_amount_field", "refund_amount_field VARCHAR(64)");
        columns.put("refund_reason_field", "refund_reason_field VARCHAR(64)");
        columns.put("refund_out_order_no_field", "refund_out_order_no_field VARCHAR(64)");
        columns.put("refund_out_refund_no_field", "refund_out_refund_no_field VARCHAR(64)");
        columns.put("refund_external_refund_id_field", "refund_external_refund_id_field VARCHAR(64)");
        columns.put("refund_item_order_id_field", "refund_item_order_id_field VARCHAR(64)");
        columns.put("refund_notify_url_field", "refund_notify_url_field VARCHAR(64)");
        columns.put("refund_amount_unit", "refund_amount_unit VARCHAR(16)");
        columns.put("refund_status_mapping", "refund_status_mapping VARCHAR(255)");
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
        columns.put("poi_id", "poi_id VARCHAR(128)");
        columns.put("verify_code_field", "verify_code_field VARCHAR(64)");
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
                WHERE provider_code = ?
                """, Integer.class, DOUYIN_PROVIDER_CODE);
        LocalDateTime now = LocalDateTime.now();
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE integration_provider_config
                    SET provider_name = ?,
                        module_code = COALESCE(module_code, 'CLUE'),
                        execution_mode = COALESCE(execution_mode, 'MOCK'),
                        auth_type = 'AUTH_CODE',
                        base_url = COALESCE(base_url, ?),
                        token_url = COALESCE(token_url, ?),
                        endpoint_path = COALESCE(endpoint_path, ?),
                        voucher_prepare_path = COALESCE(voucher_prepare_path, ?),
                        voucher_verify_path = COALESCE(voucher_verify_path, ?),
                        voucher_cancel_path = COALESCE(voucher_cancel_path, ?),
                        refund_apply_path = COALESCE(refund_apply_path, ?),
                        refund_query_path = COALESCE(refund_query_path, ?),
                        refund_list_path = COALESCE(refund_list_path, ?),
                        refund_notify_path = COALESCE(refund_notify_path, ?),
                        refund_audit_callback_path = COALESCE(refund_audit_callback_path, ?),
                        refund_order_id_field = COALESCE(refund_order_id_field, 'order_id'),
                        refund_amount_field = COALESCE(refund_amount_field, 'refund_amount'),
                        refund_reason_field = COALESCE(refund_reason_field, 'reason'),
                        refund_out_order_no_field = COALESCE(refund_out_order_no_field, 'out_order_no'),
                        refund_out_refund_no_field = COALESCE(refund_out_refund_no_field, 'out_refund_no'),
                        refund_external_refund_id_field = COALESCE(refund_external_refund_id_field, 'refund_id'),
                        refund_item_order_id_field = COALESCE(refund_item_order_id_field, 'item_order_id'),
                        refund_notify_url_field = COALESCE(refund_notify_url_field, 'notify_url'),
                        refund_amount_unit = COALESCE(refund_amount_unit, ?),
                        refund_status_mapping = COALESCE(refund_status_mapping, 'INIT,AUDITING,AUDITED,REJECTED,ARBITRATE,CANCEL,SUCCESS,FAIL'),
                        verify_code_field = COALESCE(verify_code_field, ?),
                        updated_at = ?
                    WHERE provider_code = ?
                    """,
                    DOUYIN_PROVIDER_NAME,
                    DOUYIN_BASE_URL,
                    DOUYIN_TOKEN_URL,
                    DOUYIN_CLUE_ENDPOINT,
                    DOUYIN_VOUCHER_PREPARE_PATH,
                    DOUYIN_VOUCHER_VERIFY_PATH,
                    DOUYIN_VOUCHER_CANCEL_PATH,
                    DOUYIN_REFUND_APPLY_PATH,
                    DOUYIN_REFUND_QUERY_PATH,
                    DOUYIN_REFUND_LIST_PATH,
                    DOUYIN_REFUND_NOTIFY_PATH,
                    DOUYIN_REFUND_AUDIT_CALLBACK_PATH,
                    DOUYIN_REFUND_AMOUNT_UNIT,
                    DOUYIN_VERIFY_CODE_FIELD,
                    now,
                    DOUYIN_PROVIDER_CODE);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO integration_provider_config(
                    provider_code, provider_name, module_code, execution_mode, auth_type,
                    app_id, base_url, token_url, endpoint_path,
                    voucher_prepare_path, voucher_verify_path, voucher_cancel_path,
                    refund_apply_path, refund_query_path, refund_list_path, refund_notify_path, refund_audit_callback_path,
                    refund_order_id_field, refund_amount_field, refund_reason_field,
                    refund_out_order_no_field, refund_out_refund_no_field, refund_external_refund_id_field,
                    refund_item_order_id_field, refund_notify_url_field, refund_amount_unit, refund_status_mapping,
                    verify_code_field,
                    page_size, pull_window_minutes,
                    overlap_minutes, request_timeout_ms,
                    enabled, remark, auth_status, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                DOUYIN_PROVIDER_CODE, DOUYIN_PROVIDER_NAME, "CLUE", "MOCK", "AUTH_CODE",
                null,
                DOUYIN_BASE_URL, DOUYIN_TOKEN_URL, DOUYIN_CLUE_ENDPOINT,
                DOUYIN_VOUCHER_PREPARE_PATH, DOUYIN_VOUCHER_VERIFY_PATH, DOUYIN_VOUCHER_CANCEL_PATH,
                DOUYIN_REFUND_APPLY_PATH, DOUYIN_REFUND_QUERY_PATH, DOUYIN_REFUND_LIST_PATH,
                DOUYIN_REFUND_NOTIFY_PATH, DOUYIN_REFUND_AUDIT_CALLBACK_PATH,
                "order_id", "refund_amount", "reason",
                "out_order_no", "out_refund_no", "refund_id", "item_order_id", "notify_url", DOUYIN_REFUND_AMOUNT_UNIT,
                "INIT,AUDITING,AUDITED,REJECTED,ARBITRATE,CANCEL,SUCCESS,FAIL",
                DOUYIN_VERIFY_CODE_FIELD,
                20, 60, 10, 10000,
                1, "默认使用 MOCK 模式，可切换为真实授权与核销配置", "MOCK", now, now);
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
                WHERE provider_code = ?
                LIMIT 1
                """, Long.class, DOUYIN_PROVIDER_CODE);
        if (providerId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE scheduler_job
                SET provider_id = ?
                WHERE job_code = ?
                  AND (provider_id IS NULL OR provider_id = 0)
                """, providerId, "DOUYIN_CLUE_INCREMENTAL");
    }
}
