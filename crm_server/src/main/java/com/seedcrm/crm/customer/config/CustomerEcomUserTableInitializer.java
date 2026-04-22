package com.seedcrm.crm.customer.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomerEcomUserTableInitializer {

    private static final String TABLE_NAME = "customer_ecom_user";
    private static final String UNIQUE_BINDING = "uk_customer_ecom_user_platform";

    private final JdbcTemplate jdbcTemplate;

    public CustomerEcomUserTableInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        if (!tableExists()) {
            jdbcTemplate.execute(createTableSql());
            log.info("created table {}", TABLE_NAME);
        } else {
            ensureMissingColumns();
        }
        ensureBindingIndex();
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

    private void ensureBindingIndex() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, UNIQUE_BINDING);
        if (count == null || count == 0) {
            jdbcTemplate.execute("""
                    ALTER TABLE customer_ecom_user
                    ADD UNIQUE KEY uk_customer_ecom_user_platform (customer_id, platform, ecom_user_id)
                    """);
            log.info("added unique index {} on customer_ecom_user", UNIQUE_BINDING);
        }
    }

    private String createTableSql() {
        return """
                CREATE TABLE customer_ecom_user (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    customer_id BIGINT NOT NULL,
                    platform VARCHAR(64) NOT NULL,
                    ecom_user_id VARCHAR(128) NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_customer_ecom_user_platform (customer_id, platform, ecom_user_id)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("customer_id", "customer_id BIGINT NOT NULL");
        columns.put("platform", "platform VARCHAR(64) NOT NULL");
        columns.put("ecom_user_id", "ecom_user_id VARCHAR(128) NOT NULL");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("update_time", "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }
}
