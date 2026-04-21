package com.seedcrm.crm.order.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderTableInitializer {

    private static final String TABLE_NAME = "order_info";

    private final JdbcTemplate jdbcTemplate;

    public OrderTableInitializer(DataSource dataSource) {
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
        Map<String, String> columns = expectedColumns();
        for (Map.Entry<String, String> entry : columns.entrySet()) {
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
                CREATE TABLE order_info (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    order_no VARCHAR(64) NOT NULL,
                    clue_id BIGINT,
                    customer_id BIGINT,
                    type INT NOT NULL,
                    amount DECIMAL(10,2),
                    deposit DECIMAL(10,2),
                    status VARCHAR(32) NOT NULL,
                    appointment_time DATETIME,
                    arrive_time DATETIME,
                    complete_time DATETIME,
                    remark VARCHAR(255),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_order_no (order_no)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("order_no", "order_no VARCHAR(64) NOT NULL");
        columns.put("clue_id", "clue_id BIGINT");
        columns.put("customer_id", "customer_id BIGINT");
        columns.put("type", "type INT NOT NULL");
        columns.put("amount", "amount DECIMAL(10,2)");
        columns.put("deposit", "deposit DECIMAL(10,2)");
        columns.put("status", "status VARCHAR(32) NOT NULL");
        columns.put("appointment_time", "appointment_time DATETIME");
        columns.put("arrive_time", "arrive_time DATETIME");
        columns.put("complete_time", "complete_time DATETIME");
        columns.put("remark", "remark VARCHAR(255)");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("update_time", "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }
}
