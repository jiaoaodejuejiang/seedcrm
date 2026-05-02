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
public class ClueProfileTableInitializer {

    private static final String TABLE_NAME = "clue_profile";
    private static final String INDEX_CLUE = "uk_clue_profile_clue_id";
    private static final String INDEX_UPDATED = "idx_clue_profile_updated";

    private final JdbcTemplate jdbcTemplate;

    public ClueProfileTableInitializer(DataSource dataSource) {
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
        ensureIndex(INDEX_CLUE, "ALTER TABLE " + TABLE_NAME + " ADD UNIQUE KEY " + INDEX_CLUE + " (clue_id)");
        ensureIndex(INDEX_UPDATED, "ALTER TABLE " + TABLE_NAME + " ADD INDEX " + INDEX_UPDATED + " (updated_at, id)");
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
                CREATE TABLE clue_profile (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    clue_id BIGINT NOT NULL,
                    display_name VARCHAR(64),
                    phone VARCHAR(32),
                    call_status VARCHAR(32),
                    lead_stage VARCHAR(32),
                    lead_tags_json LONGTEXT,
                    follow_records_json LONGTEXT,
                    intended_store_name VARCHAR(128),
                    assigned_at DATETIME,
                    updated_by BIGINT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_clue_profile_clue_id (clue_id),
                    KEY idx_clue_profile_updated (updated_at, id)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("clue_id", "clue_id BIGINT NOT NULL");
        columns.put("display_name", "display_name VARCHAR(64)");
        columns.put("phone", "phone VARCHAR(32)");
        columns.put("call_status", "call_status VARCHAR(32)");
        columns.put("lead_stage", "lead_stage VARCHAR(32)");
        columns.put("lead_tags_json", "lead_tags_json LONGTEXT");
        columns.put("follow_records_json", "follow_records_json LONGTEXT");
        columns.put("intended_store_name", "intended_store_name VARCHAR(128)");
        columns.put("assigned_at", "assigned_at DATETIME");
        columns.put("updated_by", "updated_by BIGINT");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }
}
