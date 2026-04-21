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
public class CustomerWecomRelationTableInitializer {

    private static final String TABLE_NAME = "customer_wecom_relation";
    private static final String UNIQUE_INDEX_CUSTOMER = "uk_customer_wecom_relation_customer_id";
    private static final String INDEX_EXTERNAL = "idx_customer_wecom_relation_external_userid";

    private final JdbcTemplate jdbcTemplate;

    public CustomerWecomRelationTableInitializer(DataSource dataSource) {
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
        ensureIndex(UNIQUE_INDEX_CUSTOMER, "ALTER TABLE " + TABLE_NAME
                + " ADD UNIQUE KEY " + UNIQUE_INDEX_CUSTOMER + " (customer_id)");
        ensureIndex(INDEX_EXTERNAL, "ALTER TABLE " + TABLE_NAME
                + " ADD INDEX " + INDEX_EXTERNAL + " (external_userid)");
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
                CREATE TABLE customer_wecom_relation (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    customer_id BIGINT NOT NULL,
                    external_userid VARCHAR(128) NOT NULL,
                    wecom_user_id VARCHAR(64),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_customer_wecom_relation_customer_id (customer_id),
                    KEY idx_customer_wecom_relation_external_userid (external_userid)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("customer_id", "customer_id BIGINT NOT NULL");
        columns.put("external_userid", "external_userid VARCHAR(128) NOT NULL");
        columns.put("wecom_user_id", "wecom_user_id VARCHAR(64)");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
