package com.seedcrm.crm.clue.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClueRecordTableInitializer {

    private static final String TABLE_NAME = "clue_record";
    private static final String INDEX_CLUE = "idx_clue_record_clue_id";
    private static final String INDEX_RECORD_KEY = "uk_clue_record_clue_key";
    private static final String INDEX_RECORD_TIME = "idx_clue_record_clue_time";
    private static final String INDEX_SOURCE_RECORD = "idx_clue_record_source_record";
    private static final String INDEX_SOURCE_ORDER = "idx_clue_record_source_order";

    private final JdbcTemplate jdbcTemplate;

    public ClueRecordTableInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        if (!tableExists()) {
            jdbcTemplate.execute(createTableSql());
            log.info("created table {}", TABLE_NAME);
            return;
        }

        ensureMissingColumns();
        ensureIndexes();
    }

    private boolean tableExists() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, TABLE_NAME);
        return count != null && count > 0;
    }

    private void ensureMissingColumns() {
        for (Map.Entry<String, String> entry : expectedColumns().entrySet()) {
            if (!columnExists(entry.getKey())) {
                jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + entry.getValue());
                log.info("added missing column {}.{}", TABLE_NAME, entry.getKey());
            }
        }
    }

    private boolean columnExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, TABLE_NAME, columnName);
        return count != null && count > 0;
    }

    private void ensureIndexes() {
        ensureIndex(INDEX_CLUE, "ALTER TABLE " + TABLE_NAME + " ADD INDEX " + INDEX_CLUE + " (clue_id)");
        ensureIndex(INDEX_RECORD_KEY, "ALTER TABLE " + TABLE_NAME
                + " ADD UNIQUE KEY " + INDEX_RECORD_KEY + " (clue_id, record_key)");
        ensureIndex(INDEX_RECORD_TIME, "ALTER TABLE " + TABLE_NAME
                + " ADD INDEX " + INDEX_RECORD_TIME + " (clue_id, occurred_at, id)");
        ensureIndex(INDEX_SOURCE_RECORD, "ALTER TABLE " + TABLE_NAME
                + " ADD INDEX " + INDEX_SOURCE_RECORD + " (source_channel, external_record_id)");
        ensureIndex(INDEX_SOURCE_ORDER, "ALTER TABLE " + TABLE_NAME
                + " ADD INDEX " + INDEX_SOURCE_ORDER + " (source_channel, external_order_id)");
    }

    private void ensureIndex(String indexName, String sql) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(sql);
            log.info("added index {} on {}", indexName, TABLE_NAME);
        }
    }

    private String createTableSql() {
        return """
                CREATE TABLE clue_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    clue_id BIGINT NOT NULL,
                    record_key VARCHAR(128) NOT NULL,
                    record_type VARCHAR(32) NOT NULL DEFAULT 'CLUE',
                    source_channel VARCHAR(32),
                    external_record_id VARCHAR(128),
                    external_order_id VARCHAR(128),
                    title VARCHAR(128),
                    content VARCHAR(500),
                    occurred_at DATETIME,
                    raw_data LONGTEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    KEY idx_clue_record_clue_id (clue_id),
                    UNIQUE KEY uk_clue_record_clue_key (clue_id, record_key),
                    KEY idx_clue_record_clue_time (clue_id, occurred_at, id),
                    KEY idx_clue_record_source_record (source_channel, external_record_id),
                    KEY idx_clue_record_source_order (source_channel, external_order_id)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("clue_id", "clue_id BIGINT NOT NULL");
        columns.put("record_key", "record_key VARCHAR(128) NOT NULL");
        columns.put("record_type", "record_type VARCHAR(32) NOT NULL DEFAULT 'CLUE'");
        columns.put("source_channel", "source_channel VARCHAR(32)");
        columns.put("external_record_id", "external_record_id VARCHAR(128)");
        columns.put("external_order_id", "external_order_id VARCHAR(128)");
        columns.put("title", "title VARCHAR(128)");
        columns.put("content", "content VARCHAR(500)");
        columns.put("occurred_at", "occurred_at DATETIME");
        columns.put("raw_data", "raw_data LONGTEXT");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }
}
