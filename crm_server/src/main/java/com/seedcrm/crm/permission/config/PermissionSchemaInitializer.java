package com.seedcrm.crm.permission.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PermissionSchemaInitializer {

    private static final String TABLE_NAME = "permission_policy";
    private static final String UNIQUE_INDEX = "uk_permission_policy_rule";

    private final JdbcTemplate jdbcTemplate;

    public PermissionSchemaInitializer(DataSource dataSource) {
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
        ensureUniqueIndex();
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

    private void ensureUniqueIndex() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, UNIQUE_INDEX);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME
                    + " ADD UNIQUE KEY " + UNIQUE_INDEX + " (module_code, action_code, role_code, data_scope)");
            log.info("added unique index {} on {}", UNIQUE_INDEX, TABLE_NAME);
        }
    }

    private String createTableSql() {
        return """
                CREATE TABLE permission_policy (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    module_code VARCHAR(64) NOT NULL,
                    action_code VARCHAR(64) NOT NULL,
                    role_code VARCHAR(64) NOT NULL,
                    data_scope VARCHAR(32) NOT NULL,
                    condition_rule VARCHAR(255),
                    is_enabled TINYINT DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_permission_policy_rule (module_code, action_code, role_code, data_scope)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("module_code", "module_code VARCHAR(64) NOT NULL");
        columns.put("action_code", "action_code VARCHAR(64) NOT NULL");
        columns.put("role_code", "role_code VARCHAR(64) NOT NULL");
        columns.put("data_scope", "data_scope VARCHAR(32) NOT NULL");
        columns.put("condition_rule", "condition_rule VARCHAR(255)");
        columns.put("is_enabled", "is_enabled TINYINT DEFAULT 1");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }
}
