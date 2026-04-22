package com.seedcrm.crm.distributor.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DistributorTableInitializer {

    private static final String TABLE_NAME = "distributor";

    private final JdbcTemplate jdbcTemplate;

    public DistributorTableInitializer(DataSource dataSource) {
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

    private String createTableSql() {
        return """
                CREATE TABLE distributor (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(128) NOT NULL,
                    contact_info VARCHAR(255),
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("name", "name VARCHAR(128) NOT NULL");
        columns.put("contact_info", "contact_info VARCHAR(255)");
        columns.put("status", "status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
