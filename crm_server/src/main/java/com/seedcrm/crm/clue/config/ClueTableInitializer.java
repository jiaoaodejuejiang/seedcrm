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
public class ClueTableInitializer {

    private static final String TABLE_NAME = "clue";
    private static final String INDEX_SOURCE_CREATED = "idx_clue_source_created";

    private final JdbcTemplate jdbcTemplate;

    public ClueTableInitializer(DataSource dataSource) {
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
        ensureUniqueIndex("uk_clue_phone", "phone");
        ensureUniqueIndex("uk_clue_wechat", "wechat");
        ensureIndex(INDEX_SOURCE_CREATED, "ALTER TABLE " + TABLE_NAME
                + " ADD INDEX " + INDEX_SOURCE_CREATED + " (source_channel, created_at, id)");
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

    private void ensureUniqueIndex(String indexName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD UNIQUE KEY " + indexName + " (" + columnName + ")");
            log.info("added unique index {} on {}.{}", indexName, TABLE_NAME, columnName);
        }
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
                CREATE TABLE clue (
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
                    UNIQUE KEY uk_clue_wechat (wechat),
                    KEY idx_clue_source_created (source_channel, created_at, id)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("phone", "phone VARCHAR(32)");
        columns.put("wechat", "wechat VARCHAR(64)");
        columns.put("name", "name VARCHAR(128)");
        columns.put("source", "source VARCHAR(64)");
        columns.put("source_channel", "source_channel VARCHAR(32)");
        columns.put("source_id", "source_id BIGINT");
        columns.put("raw_data", "raw_data LONGTEXT");
        columns.put("status", "status VARCHAR(32)");
        columns.put("current_owner_id", "current_owner_id BIGINT");
        columns.put("is_public", "is_public TINYINT DEFAULT 1");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }
}
