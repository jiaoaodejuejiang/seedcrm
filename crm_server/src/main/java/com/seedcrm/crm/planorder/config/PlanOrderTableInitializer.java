package com.seedcrm.crm.planorder.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlanOrderTableInitializer {

    private static final String TABLE_NAME = "plan_order";
    private static final String UNIQUE_INDEX = "uk_plan_order_order_id";

    private final JdbcTemplate jdbcTemplate;

    public PlanOrderTableInitializer(DataSource dataSource) {
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
        ensureOrderUniqueIndex();
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

    private void ensureOrderUniqueIndex() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, UNIQUE_INDEX);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD UNIQUE KEY "
                    + UNIQUE_INDEX + " (order_id)");
            log.info("added unique index {} on {}.order_id", UNIQUE_INDEX, TABLE_NAME);
        }
    }

    private String createTableSql() {
        return """
                CREATE TABLE plan_order (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    order_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    arrive_time DATETIME,
                    start_time DATETIME,
                    finish_time DATETIME,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_plan_order_order_id (order_id)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("order_id", "order_id BIGINT NOT NULL");
        columns.put("status", "status VARCHAR(32) NOT NULL");
        columns.put("arrive_time", "arrive_time DATETIME");
        columns.put("start_time", "start_time DATETIME");
        columns.put("finish_time", "finish_time DATETIME");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
