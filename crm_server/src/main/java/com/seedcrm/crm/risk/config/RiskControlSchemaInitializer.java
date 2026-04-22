package com.seedcrm.crm.risk.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RiskControlSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public RiskControlSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        ensureTable("idempotent_record", createIdempotentRecordSql(), idempotentRecordColumns());
        ensureIndex("idempotent_record", "uk_idempotent_record_biz_key",
                "CREATE UNIQUE INDEX uk_idempotent_record_biz_key ON idempotent_record(biz_key)");
        ensureIndex("idempotent_record", "idx_idempotent_record_biz_type_status",
                "CREATE INDEX idx_idempotent_record_biz_type_status ON idempotent_record(biz_type, status)");
    }

    private void ensureTable(String tableName, String createSql, Map<String, String> expectedColumns) {
        if (!tableExists(tableName)) {
            jdbcTemplate.execute(createSql);
            log.info("created table {}", tableName);
            return;
        }
        for (Map.Entry<String, String> entry : expectedColumns.entrySet()) {
            if (!columnExists(tableName, entry.getKey())) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + entry.getValue());
                log.info("added missing column {}.{}", tableName, entry.getKey());
            }
        }
    }

    private void ensureIndex(String tableName, String indexName, String createIndexSql) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(createIndexSql);
            log.info("created index {} on {}", indexName, tableName);
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

    private String createIdempotentRecordSql() {
        return """
                CREATE TABLE idempotent_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    biz_key VARCHAR(128) NOT NULL,
                    biz_type VARCHAR(32) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """;
    }

    private Map<String, String> idempotentRecordColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("biz_key", "biz_key VARCHAR(128) NOT NULL");
        columns.put("biz_type", "biz_type VARCHAR(32) NOT NULL");
        columns.put("status", "status VARCHAR(16) NOT NULL");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
