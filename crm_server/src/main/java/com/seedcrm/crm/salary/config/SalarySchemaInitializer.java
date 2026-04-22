package com.seedcrm.crm.salary.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SalarySchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public SalarySchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        ensureTable("salary_rule", salaryRuleCreateSql(), salaryRuleColumns());
        ensureTable("salary_detail", salaryDetailCreateSql(), salaryDetailColumns());
        ensureTable("salary_settlement", salarySettlementCreateSql(), salarySettlementColumns());
        ensureTable("withdraw_record", withdrawRecordCreateSql(), withdrawRecordColumns());
        ensureIndex("salary_detail", "uk_salary_detail_plan_user_role",
                "CREATE UNIQUE INDEX uk_salary_detail_plan_user_role ON salary_detail(plan_order_id, user_id, role_code)");
    }

    private void ensureTable(String tableName, String createSql, Map<String, String> expectedColumns) {
        if (!tableExists(tableName)) {
            jdbcTemplate.execute(createSql);
            log.info("created table {}", tableName);
            return;
        }
        ensureMissingColumns(tableName, expectedColumns);
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

    private void ensureMissingColumns(String tableName, Map<String, String> expectedColumns) {
        for (Map.Entry<String, String> entry : expectedColumns.entrySet()) {
            if (!columnExists(tableName, entry.getKey())) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + entry.getValue());
                log.info("added missing column {}.{}", tableName, entry.getKey());
            }
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private void ensureIndex(String tableName, String indexName, String createIndexSql) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(createIndexSql);
            log.info("created index {} on {}", indexName, tableName);
        }
    }

    private String salaryRuleCreateSql() {
        return """
                CREATE TABLE salary_rule (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    role_code VARCHAR(32) NOT NULL,
                    rule_type VARCHAR(16) NOT NULL,
                    rule_value DECIMAL(10,4) NOT NULL,
                    is_active TINYINT NOT NULL DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_salary_rule_role_active (role_code, is_active)
                )
                """;
    }

    private Map<String, String> salaryRuleColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("role_code", "role_code VARCHAR(32) NOT NULL");
        columns.put("rule_type", "rule_type VARCHAR(16) NOT NULL");
        columns.put("rule_value", "rule_value DECIMAL(10,4) NOT NULL");
        columns.put("is_active", "is_active TINYINT NOT NULL DEFAULT 1");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private String salaryDetailCreateSql() {
        return """
                CREATE TABLE salary_detail (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    plan_order_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    role_code VARCHAR(32) NOT NULL,
                    order_amount DECIMAL(12,2) NOT NULL,
                    amount DECIMAL(12,2) NOT NULL,
                    settlement_id BIGINT,
                    settlement_time DATETIME,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_salary_detail_plan_order_id (plan_order_id),
                    KEY idx_salary_detail_user_id (user_id),
                    KEY idx_salary_detail_settlement_id (settlement_id)
                )
                """;
    }

    private Map<String, String> salaryDetailColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("plan_order_id", "plan_order_id BIGINT NOT NULL");
        columns.put("user_id", "user_id BIGINT NOT NULL");
        columns.put("role_code", "role_code VARCHAR(32) NOT NULL");
        columns.put("order_amount", "order_amount DECIMAL(12,2) NOT NULL");
        columns.put("amount", "amount DECIMAL(12,2) NOT NULL");
        columns.put("settlement_id", "settlement_id BIGINT");
        columns.put("settlement_time", "settlement_time DATETIME");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private String salarySettlementCreateSql() {
        return """
                CREATE TABLE salary_settlement (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    total_amount DECIMAL(12,2) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    start_time DATETIME NOT NULL,
                    end_time DATETIME NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_salary_settlement_user_id (user_id),
                    KEY idx_salary_settlement_status (status)
                )
                """;
    }

    private Map<String, String> salarySettlementColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("user_id", "user_id BIGINT NOT NULL");
        columns.put("total_amount", "total_amount DECIMAL(12,2) NOT NULL");
        columns.put("status", "status VARCHAR(16) NOT NULL");
        columns.put("start_time", "start_time DATETIME NOT NULL");
        columns.put("end_time", "end_time DATETIME NOT NULL");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private String withdrawRecordCreateSql() {
        return """
                CREATE TABLE withdraw_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    amount DECIMAL(12,2) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_withdraw_record_user_id (user_id),
                    KEY idx_withdraw_record_status (status)
                )
                """;
    }

    private Map<String, String> withdrawRecordColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("user_id", "user_id BIGINT NOT NULL");
        columns.put("amount", "amount DECIMAL(12,2) NOT NULL");
        columns.put("status", "status VARCHAR(16) NOT NULL");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
