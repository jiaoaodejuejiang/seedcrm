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
public class SchedulerSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public SchedulerSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        ensureTable("scheduler_job", createSchedulerJobSql(), schedulerJobColumns());
        ensureTable("scheduler_job_log", createSchedulerLogSql(), schedulerLogColumns());
        ensureTable("scheduler_job_audit_log", createSchedulerAuditLogSql(), schedulerAuditLogColumns());
        ensureTable("scheduler_outbox_event", createSchedulerOutboxEventSql(), schedulerOutboxEventColumns());
        ensureTable("distribution_exception_record", createDistributionExceptionRecordSql(), distributionExceptionRecordColumns());
        seedDefaultJob();
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

    private String createSchedulerJobSql() {
        return """
                CREATE TABLE scheduler_job (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    job_code VARCHAR(64) NOT NULL,
                    module_code VARCHAR(64) NOT NULL,
                    sync_mode VARCHAR(32) NOT NULL,
                    interval_minutes INT DEFAULT 1,
                    retry_limit INT DEFAULT 3,
                    queue_name VARCHAR(64),
                    provider_id BIGINT,
                    endpoint VARCHAR(255),
                    status VARCHAR(32) DEFAULT 'ENABLED',
                    last_run_time DATETIME,
                    next_run_time DATETIME,
                    lock_owner VARCHAR(128),
                    lock_until DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_scheduler_job_code (job_code)
                )
                """;
    }

    private String createSchedulerAuditLogSql() {
        return """
                CREATE TABLE scheduler_job_audit_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    job_code VARCHAR(64),
                    log_id BIGINT,
                    action_type VARCHAR(32) NOT NULL,
                    actor_type VARCHAR(32) NOT NULL,
                    actor_user_id BIGINT,
                    actor_role_code VARCHAR(64),
                    status VARCHAR(32),
                    summary VARCHAR(255),
                    detail TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_scheduler_audit_job_time (job_code, created_at),
                    KEY idx_scheduler_audit_log_id (log_id)
                )
                """;
    }

    private String createSchedulerLogSql() {
        return """
                CREATE TABLE scheduler_job_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    job_code VARCHAR(64) NOT NULL,
                    queue_name VARCHAR(64),
                    provider_id BIGINT,
                    sync_mode VARCHAR(32),
                    trigger_type VARCHAR(32),
                    status VARCHAR(32) NOT NULL,
                    retry_count INT DEFAULT 0,
                    payload LONGTEXT,
                    error_message VARCHAR(255),
                    next_retry_time DATETIME,
                    started_at DATETIME,
                    finished_at DATETIME,
                    duration_ms BIGINT,
                    imported_count INT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_scheduler_job_log_job_code (job_code),
                    KEY idx_scheduler_job_log_status (status),
                    KEY idx_scheduler_job_log_retry (status, next_retry_time)
                )
                """;
    }

    private String createSchedulerOutboxEventSql() {
        return """
                CREATE TABLE scheduler_outbox_event (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    event_key VARCHAR(128) NOT NULL,
                    event_type VARCHAR(128) NOT NULL,
                    provider_code VARCHAR(64),
                    related_order_id BIGINT,
                    related_plan_order_id BIGINT,
                    external_partner_code VARCHAR(64),
                    external_order_id VARCHAR(128),
                    payload LONGTEXT,
                    destination_url VARCHAR(512),
                    status VARCHAR(32) NOT NULL,
                    retry_count INT DEFAULT 0,
                    next_retry_time DATETIME,
                    last_error VARCHAR(512),
                    last_response TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    sent_at DATETIME,
                    UNIQUE KEY uk_outbox_event_key (event_key),
                    KEY idx_outbox_status_retry (status, next_retry_time),
                    KEY idx_outbox_order (related_order_id)
                )
                """;
    }

    private String createDistributionExceptionRecordSql() {
        return """
                CREATE TABLE distribution_exception_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    partner_code VARCHAR(64),
                    event_type VARCHAR(128),
                    event_id VARCHAR(128),
                    idempotency_key VARCHAR(128),
                    external_order_id VARCHAR(128),
                    related_order_id BIGINT,
                    related_order_no VARCHAR(64),
                    external_member_id VARCHAR(128),
                    phone VARCHAR(32),
                    error_code VARCHAR(64),
                    error_message VARCHAR(512),
                    conflict_detail_json LONGTEXT,
                    raw_payload LONGTEXT,
                    callback_log_trace_id VARCHAR(64),
                    handling_status VARCHAR(32) NOT NULL,
                    retry_count INT DEFAULT 0,
                    next_retry_time DATETIME,
                    handler_user_id BIGINT,
                    handler_role_code VARCHAR(64),
                    handle_remark VARCHAR(512),
                    handled_at DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    KEY idx_distribution_exception_status (handling_status, created_at),
                    KEY idx_distribution_exception_order (partner_code, external_order_id),
                    KEY idx_distribution_exception_related_order (related_order_id),
                    KEY idx_distribution_exception_idempotency (idempotency_key)
                )
                """;
    }

    private Map<String, String> schedulerJobColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("job_code", "job_code VARCHAR(64) NOT NULL");
        columns.put("module_code", "module_code VARCHAR(64) NOT NULL");
        columns.put("sync_mode", "sync_mode VARCHAR(32) NOT NULL");
        columns.put("interval_minutes", "interval_minutes INT DEFAULT 1");
        columns.put("retry_limit", "retry_limit INT DEFAULT 3");
        columns.put("queue_name", "queue_name VARCHAR(64)");
        columns.put("provider_id", "provider_id BIGINT");
        columns.put("endpoint", "endpoint VARCHAR(255)");
        columns.put("status", "status VARCHAR(32) DEFAULT 'ENABLED'");
        columns.put("last_run_time", "last_run_time DATETIME");
        columns.put("next_run_time", "next_run_time DATETIME");
        columns.put("lock_owner", "lock_owner VARCHAR(128)");
        columns.put("lock_until", "lock_until DATETIME");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }

    private Map<String, String> schedulerLogColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("job_code", "job_code VARCHAR(64) NOT NULL");
        columns.put("queue_name", "queue_name VARCHAR(64)");
        columns.put("provider_id", "provider_id BIGINT");
        columns.put("sync_mode", "sync_mode VARCHAR(32)");
        columns.put("trigger_type", "trigger_type VARCHAR(32)");
        columns.put("status", "status VARCHAR(32) NOT NULL");
        columns.put("retry_count", "retry_count INT DEFAULT 0");
        columns.put("payload", "payload LONGTEXT");
        columns.put("error_message", "error_message VARCHAR(255)");
        columns.put("next_retry_time", "next_retry_time DATETIME");
        columns.put("started_at", "started_at DATETIME");
        columns.put("finished_at", "finished_at DATETIME");
        columns.put("duration_ms", "duration_ms BIGINT");
        columns.put("imported_count", "imported_count INT");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private Map<String, String> schedulerAuditLogColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("job_code", "job_code VARCHAR(64)");
        columns.put("log_id", "log_id BIGINT");
        columns.put("action_type", "action_type VARCHAR(32) NOT NULL");
        columns.put("actor_type", "actor_type VARCHAR(32) NOT NULL");
        columns.put("actor_user_id", "actor_user_id BIGINT");
        columns.put("actor_role_code", "actor_role_code VARCHAR(64)");
        columns.put("status", "status VARCHAR(32)");
        columns.put("summary", "summary VARCHAR(255)");
        columns.put("detail", "detail TEXT");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private Map<String, String> schedulerOutboxEventColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("event_key", "event_key VARCHAR(128) NOT NULL");
        columns.put("event_type", "event_type VARCHAR(128) NOT NULL");
        columns.put("provider_code", "provider_code VARCHAR(64)");
        columns.put("related_order_id", "related_order_id BIGINT");
        columns.put("related_plan_order_id", "related_plan_order_id BIGINT");
        columns.put("external_partner_code", "external_partner_code VARCHAR(64)");
        columns.put("external_order_id", "external_order_id VARCHAR(128)");
        columns.put("payload", "payload LONGTEXT");
        columns.put("destination_url", "destination_url VARCHAR(512)");
        columns.put("status", "status VARCHAR(32) NOT NULL");
        columns.put("retry_count", "retry_count INT DEFAULT 0");
        columns.put("next_retry_time", "next_retry_time DATETIME");
        columns.put("last_error", "last_error VARCHAR(512)");
        columns.put("last_response", "last_response TEXT");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        columns.put("sent_at", "sent_at DATETIME");
        return columns;
    }

    private Map<String, String> distributionExceptionRecordColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("partner_code", "partner_code VARCHAR(64)");
        columns.put("event_type", "event_type VARCHAR(128)");
        columns.put("event_id", "event_id VARCHAR(128)");
        columns.put("idempotency_key", "idempotency_key VARCHAR(128)");
        columns.put("external_order_id", "external_order_id VARCHAR(128)");
        columns.put("related_order_id", "related_order_id BIGINT");
        columns.put("related_order_no", "related_order_no VARCHAR(64)");
        columns.put("external_member_id", "external_member_id VARCHAR(128)");
        columns.put("phone", "phone VARCHAR(32)");
        columns.put("error_code", "error_code VARCHAR(64)");
        columns.put("error_message", "error_message VARCHAR(512)");
        columns.put("conflict_detail_json", "conflict_detail_json LONGTEXT");
        columns.put("raw_payload", "raw_payload LONGTEXT");
        columns.put("callback_log_trace_id", "callback_log_trace_id VARCHAR(64)");
        columns.put("handling_status", "handling_status VARCHAR(32) NOT NULL");
        columns.put("retry_count", "retry_count INT DEFAULT 0");
        columns.put("next_retry_time", "next_retry_time DATETIME");
        columns.put("handler_user_id", "handler_user_id BIGINT");
        columns.put("handler_role_code", "handler_role_code VARCHAR(64)");
        columns.put("handle_remark", "handle_remark VARCHAR(512)");
        columns.put("handled_at", "handled_at DATETIME");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }

    private void seedDefaultJob() {
        seedJobIfMissing("DOUYIN_CLUE_INCREMENTAL", "CLUE", "INCREMENTAL", 1, 3,
                "douyin-clue-sync", "/clue/add", "ENABLED");
        seedJobIfMissing("DISTRIBUTION_OUTBOX_PROCESS", "DISTRIBUTION", "INCREMENTAL", 1, 5,
                "distribution-outbox", "/scheduler/outbox/process", "ENABLED");
        seedJobIfMissing("DISTRIBUTION_EXCEPTION_RETRY", "DISTRIBUTION", "INCREMENTAL", 1, 5,
                "distribution-exception-retry", "/scheduler/distribution/exceptions/process", "ENABLED");
        seedJobIfMissing("DISTRIBUTION_STATUS_CHECK", "DISTRIBUTION", "INCREMENTAL", 5, 5,
                "distribution-status-check", "/scheduler/distribution/status-check/process", "ENABLED");
        seedJobIfMissing("DISTRIBUTION_RECONCILE_PULL", "DISTRIBUTION", "INCREMENTAL", 10, 5,
                "distribution-reconcile-pull", "/scheduler/distribution/reconcile/process", "ENABLED");
    }

    private void seedJobIfMissing(String jobCode,
                                  String moduleCode,
                                  String syncMode,
                                  int intervalMinutes,
                                  int retryLimit,
                                  String queueName,
                                  String endpoint,
                                  String status) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM scheduler_job
                WHERE job_code = ?
                """, Integer.class, jobCode);
        if (count != null && count > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO scheduler_job(job_code, module_code, sync_mode, interval_minutes, retry_limit, queue_name, provider_id, endpoint, status, next_run_time, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                jobCode, moduleCode, syncMode, intervalMinutes, retryLimit, queueName, null,
                endpoint, status, now.plusMinutes(intervalMinutes), now, now);
    }
}
