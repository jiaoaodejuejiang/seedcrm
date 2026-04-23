package com.seedcrm.crm.scheduler.config;

import jakarta.annotation.PostConstruct;
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
                    endpoint VARCHAR(255),
                    status VARCHAR(32) DEFAULT 'ENABLED',
                    last_run_time DATETIME,
                    next_run_time DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_scheduler_job_code (job_code)
                )
                """;
    }

    private String createSchedulerLogSql() {
        return """
                CREATE TABLE scheduler_job_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    job_code VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    retry_count INT DEFAULT 0,
                    payload LONGTEXT,
                    error_message VARCHAR(255),
                    next_retry_time DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_scheduler_job_log_job_code (job_code),
                    KEY idx_scheduler_job_log_status (status)
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
        columns.put("endpoint", "endpoint VARCHAR(255)");
        columns.put("status", "status VARCHAR(32) DEFAULT 'ENABLED'");
        columns.put("last_run_time", "last_run_time DATETIME");
        columns.put("next_run_time", "next_run_time DATETIME");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }

    private Map<String, String> schedulerLogColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("job_code", "job_code VARCHAR(64) NOT NULL");
        columns.put("status", "status VARCHAR(32) NOT NULL");
        columns.put("retry_count", "retry_count INT DEFAULT 0");
        columns.put("payload", "payload LONGTEXT");
        columns.put("error_message", "error_message VARCHAR(255)");
        columns.put("next_retry_time", "next_retry_time DATETIME");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
