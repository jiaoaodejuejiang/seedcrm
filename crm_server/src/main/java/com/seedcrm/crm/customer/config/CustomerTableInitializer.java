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
public class CustomerTableInitializer {

    private static final String TABLE_NAME = "customer";
    private static final String UNIQUE_INDEX = "uk_customer_phone";
    private static final String EXTERNAL_MEMBER_INDEX = "uk_customer_external_member";

    private final JdbcTemplate jdbcTemplate;

    public CustomerTableInitializer(DataSource dataSource) {
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
        ensurePhoneUniqueIndex();
        ensureExternalMemberIndex();
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

    private void ensurePhoneUniqueIndex() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, UNIQUE_INDEX);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD UNIQUE KEY " + UNIQUE_INDEX + " (phone)");
            log.info("added unique index {} on {}.phone", UNIQUE_INDEX, TABLE_NAME);
        }
    }

    private void ensureExternalMemberIndex() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, EXTERNAL_MEMBER_INDEX);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME
                    + " ADD UNIQUE KEY " + EXTERNAL_MEMBER_INDEX + " (external_partner_code, external_member_id)");
            log.info("added unique index {} on customer external member", EXTERNAL_MEMBER_INDEX);
        }
    }

    private String createTableSql() {
        return """
                CREATE TABLE customer (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(128),
                    phone VARCHAR(32) NOT NULL,
                    wechat VARCHAR(64),
                    source_clue_id BIGINT,
                    source_channel VARCHAR(32),
                    source VARCHAR(32),
                    source_id BIGINT,
                    external_partner_code VARCHAR(64),
                    external_member_id VARCHAR(128),
                    external_member_role VARCHAR(64),
                    raw_data TEXT,
                    status VARCHAR(32) NOT NULL,
                    level VARCHAR(32),
                    tag VARCHAR(64),
                    first_order_time DATETIME,
                    last_order_time DATETIME,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_customer_phone (phone)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("name", "name VARCHAR(128)");
        columns.put("phone", "phone VARCHAR(32) NOT NULL");
        columns.put("wechat", "wechat VARCHAR(64)");
        columns.put("source_clue_id", "source_clue_id BIGINT");
        columns.put("source_channel", "source_channel VARCHAR(32)");
        columns.put("source", "source VARCHAR(32)");
        columns.put("source_id", "source_id BIGINT");
        columns.put("external_partner_code", "external_partner_code VARCHAR(64)");
        columns.put("external_member_id", "external_member_id VARCHAR(128)");
        columns.put("external_member_role", "external_member_role VARCHAR(64)");
        columns.put("raw_data", "raw_data TEXT");
        columns.put("status", "status VARCHAR(32) NOT NULL DEFAULT 'NEW'");
        columns.put("level", "level VARCHAR(32)");
        columns.put("tag", "tag VARCHAR(64)");
        columns.put("first_order_time", "first_order_time DATETIME");
        columns.put("last_order_time", "last_order_time DATETIME");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("update_time", "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }
}
