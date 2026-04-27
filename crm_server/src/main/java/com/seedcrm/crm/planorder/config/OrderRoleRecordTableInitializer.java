package com.seedcrm.crm.planorder.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DependsOn({"planOrderTableInitializer", "orderTableInitializer"})
public class OrderRoleRecordTableInitializer {

    private static final String TABLE_NAME = "order_role_record";

    private final JdbcTemplate jdbcTemplate;

    public OrderRoleRecordTableInitializer(DataSource dataSource) {
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
        migrateLegacyStoreServiceAssignments();
        backfillOpenPlanOrderAssignments();
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
                CREATE TABLE order_role_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    plan_order_id BIGINT NOT NULL,
                    role_code VARCHAR(32) NOT NULL,
                    user_id BIGINT NOT NULL,
                    start_time DATETIME NOT NULL,
                    end_time DATETIME,
                    is_current TINYINT NOT NULL DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_order_role_record_plan_order_id (plan_order_id),
                    KEY idx_order_role_record_user_id (user_id),
                    KEY idx_order_role_record_plan_role_current (plan_order_id, role_code, is_current)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("plan_order_id", "plan_order_id BIGINT NOT NULL");
        columns.put("role_code", "role_code VARCHAR(32) NOT NULL");
        columns.put("user_id", "user_id BIGINT NOT NULL");
        columns.put("start_time", "start_time DATETIME NOT NULL");
        columns.put("end_time", "end_time DATETIME");
        columns.put("is_current", "is_current TINYINT NOT NULL DEFAULT 1");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private void backfillOpenPlanOrderAssignments() {
        int inserted = jdbcTemplate.update("""
                INSERT INTO order_role_record (plan_order_id, role_code, user_id, start_time, is_current, create_time)
                SELECT p.id, 'STORE_SERVICE', 5101, NOW(), 1, NOW()
                FROM plan_order p
                JOIN order_info o ON o.id = p.order_id
                LEFT JOIN order_role_record current_role
                    ON current_role.plan_order_id = p.id
                   AND current_role.is_current = 1
                WHERE current_role.id IS NULL
                  AND o.status IN ('APPOINTMENT', 'ARRIVED', 'SERVING')
                """);
        if (inserted > 0) {
            log.info("backfilled {} open plan orders without current store assignment", inserted);
        }
    }

    private void migrateLegacyStoreServiceAssignments() {
        int updated = jdbcTemplate.update("""
                UPDATE order_role_record
                SET user_id = 5101
                WHERE role_code = 'STORE_SERVICE'
                  AND user_id = 1001
                """);
        if (updated > 0) {
            log.info("migrated {} legacy store-service assignments to user 5101", updated);
        }
    }
}
