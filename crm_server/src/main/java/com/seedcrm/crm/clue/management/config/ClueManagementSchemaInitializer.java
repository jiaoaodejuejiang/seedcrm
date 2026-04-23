package com.seedcrm.crm.clue.management.config;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClueManagementSchemaInitializer {

    private static final String STRATEGY_TABLE = "clue_assignment_strategy";
    private static final String DUTY_TABLE = "duty_customer_service";

    private final JdbcTemplate jdbcTemplate;

    public ClueManagementSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        ensureStrategyTable();
        ensureDutyTable();
        seedDefaults();
    }

    private void ensureStrategyTable() {
        if (!tableExists(STRATEGY_TABLE)) {
            jdbcTemplate.execute("""
                    CREATE TABLE clue_assignment_strategy (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        store_id BIGINT NOT NULL,
                        enabled TINYINT NOT NULL DEFAULT 1,
                        assignment_mode VARCHAR(32) NOT NULL,
                        last_assigned_user_id BIGINT,
                        updated_by BIGINT,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_clue_assignment_strategy_store (store_id)
                    )
                    """);
            log.info("created table {}", STRATEGY_TABLE);
        }
    }

    private void ensureDutyTable() {
        if (!tableExists(DUTY_TABLE)) {
            jdbcTemplate.execute("""
                    CREATE TABLE duty_customer_service (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        store_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        username VARCHAR(64),
                        user_name VARCHAR(64) NOT NULL,
                        shift_label VARCHAR(64),
                        on_duty TINYINT NOT NULL DEFAULT 1,
                        on_leave TINYINT NOT NULL DEFAULT 0,
                        sort_order INT NOT NULL DEFAULT 1,
                        remark VARCHAR(255),
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_duty_customer_service_store_user (store_id, user_id)
                    )
                    """);
            log.info("created table {}", DUTY_TABLE);
        }
    }

    private void seedDefaults() {
        Integer strategyCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + STRATEGY_TABLE, Integer.class);
        if (strategyCount != null && strategyCount == 0) {
            jdbcTemplate.update("""
                    INSERT INTO clue_assignment_strategy (store_id, enabled, assignment_mode, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, NOW())
                    """, 10L, 1, "ROUND_ROBIN", 5001L);
        }

        Integer dutyCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + DUTY_TABLE, Integer.class);
        if (dutyCount != null && dutyCount == 0) {
            jdbcTemplate.update("""
                    INSERT INTO duty_customer_service
                    (store_id, user_id, username, user_name, shift_label, on_duty, on_leave, sort_order, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, 10L, 1001L, "online_cs_a", "客服A", "早班 09:00-18:00", 1, 0, 1, "默认当值客服");
            jdbcTemplate.update("""
                    INSERT INTO duty_customer_service
                    (store_id, user_id, username, user_name, shift_label, on_duty, on_leave, sort_order, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, 10L, 1002L, "online_cs_b", "客服B", "晚班 13:00-22:00", 1, 0, 2, "默认当值客服");
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }
}
