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
public class CustomerTagDetailTableInitializer {

    private static final String TABLE_NAME = "customer_tag_detail";
    private static final String INDEX_CUSTOMER_ID = "idx_customer_tag_detail_customer_id";

    private final JdbcTemplate jdbcTemplate;

    public CustomerTagDetailTableInitializer(DataSource dataSource) {
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
        ensureCustomerIndex();
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

    private void ensureCustomerIndex() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, INDEX_CUSTOMER_ID);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD KEY " + INDEX_CUSTOMER_ID + " (customer_id)");
            log.info("added index {} on {}.customer_id", INDEX_CUSTOMER_ID, TABLE_NAME);
        }
    }

    private String createTableSql() {
        return """
                CREATE TABLE customer_tag_detail (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    customer_id BIGINT NOT NULL,
                    tag_code VARCHAR(64) NOT NULL,
                    tag_name VARCHAR(128),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    KEY idx_customer_tag_detail_customer_id (customer_id)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("customer_id", "customer_id BIGINT NOT NULL");
        columns.put("tag_code", "tag_code VARCHAR(64) NOT NULL");
        columns.put("tag_name", "tag_name VARCHAR(128)");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("update_time", "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }
}
