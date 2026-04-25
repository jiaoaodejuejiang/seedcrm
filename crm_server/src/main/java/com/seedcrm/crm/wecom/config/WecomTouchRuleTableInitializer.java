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
public class WecomTouchRuleTableInitializer {

    private static final String TABLE_NAME = "wecom_touch_rule";
    private static final String INDEX_TAG_TRIGGER = "idx_wecom_touch_rule_tag_trigger";

    private final JdbcTemplate jdbcTemplate;

    public WecomTouchRuleTableInitializer(DataSource dataSource) {
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
        ensureIndex();
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

    private void ensureIndex() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, INDEX_TAG_TRIGGER);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME
                    + " ADD INDEX " + INDEX_TAG_TRIGGER + " (tag, trigger_type)");
            log.info("added index {} on {}", INDEX_TAG_TRIGGER, TABLE_NAME);
        }
    }

    private String createTableSql() {
        return """
                CREATE TABLE wecom_touch_rule (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    tag VARCHAR(64) NOT NULL,
                    rule_name VARCHAR(128),
                    message_template TEXT,
                    trigger_type VARCHAR(16) NOT NULL,
                    is_enabled TINYINT DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_wecom_touch_rule_tag_trigger (tag, trigger_type)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("tag", "tag VARCHAR(64) NOT NULL");
        columns.put("rule_name", "rule_name VARCHAR(128)");
        columns.put("message_template", "message_template TEXT");
        columns.put("trigger_type", "trigger_type VARCHAR(16) NOT NULL");
        columns.put("is_enabled", "is_enabled TINYINT DEFAULT 1");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
