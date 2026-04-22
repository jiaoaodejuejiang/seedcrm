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
public class DistributorBenefitSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public DistributorBenefitSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        ensureTable("distributor_rule", createDistributorRuleSql(), distributorRuleColumns());
        ensureTable("distributor_income_detail", createDistributorIncomeDetailSql(), distributorIncomeDetailColumns());
        ensureTable("distributor_settlement", createDistributorSettlementSql(), distributorSettlementColumns());
        ensureTable("distributor_withdraw", createDistributorWithdrawSql(), distributorWithdrawColumns());
        dropIndexIfExists("distributor_income_detail", "uk_distributor_income_detail_order_id");
        ensureUniqueIndex("distributor_income_detail", "uk_distributor_income_detail_order_distributor",
                "CREATE UNIQUE INDEX uk_distributor_income_detail_order_distributor ON distributor_income_detail(order_id, distributor_id)");
    }

    private void ensureTable(String tableName, String createSql, Map<String, String> expectedColumns) {
        if (!tableExists(tableName)) {
            jdbcTemplate.execute(createSql);
            log.info("created table {}", tableName);
            return;
        }
        for (Map.Entry<String, String> entry : expectedColumns.entrySet()) {
            if (!columnExists(tableName, entry.getKey())) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + entry.getValue());
                log.info("added missing column {}.{}", tableName, entry.getKey());
            }
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

    private void ensureUniqueIndex(String tableName, String indexName, String createIndexSql) {
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

    private void dropIndexIfExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName);
        if (count != null && count > 0) {
            jdbcTemplate.execute("DROP INDEX " + indexName + " ON " + tableName);
            log.info("dropped index {} on {}", indexName, tableName);
        }
    }

    private String createDistributorRuleSql() {
        return """
                CREATE TABLE distributor_rule (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    distributor_id BIGINT NOT NULL,
                    rule_type VARCHAR(16) NOT NULL,
                    rule_value DECIMAL(10,4) NOT NULL,
                    is_active TINYINT NOT NULL DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_distributor_rule_distributor_active (distributor_id, is_active)
                )
                """;
    }

    private String createDistributorIncomeDetailSql() {
        return """
                CREATE TABLE distributor_income_detail (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    distributor_id BIGINT NOT NULL,
                    order_id BIGINT NOT NULL,
                    order_amount DECIMAL(12,2) NOT NULL,
                    income_amount DECIMAL(12,2) NOT NULL,
                    settlement_id BIGINT,
                    settlement_time DATETIME,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_distributor_income_detail_distributor_id (distributor_id),
                    KEY idx_distributor_income_detail_settlement_id (settlement_id)
                )
                """;
    }

    private String createDistributorSettlementSql() {
        return """
                CREATE TABLE distributor_settlement (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    distributor_id BIGINT NOT NULL,
                    total_amount DECIMAL(12,2) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    start_time DATETIME NOT NULL,
                    end_time DATETIME NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_distributor_settlement_distributor_id (distributor_id),
                    KEY idx_distributor_settlement_status (status)
                )
                """;
    }

    private String createDistributorWithdrawSql() {
        return """
                CREATE TABLE distributor_withdraw (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    distributor_id BIGINT NOT NULL,
                    amount DECIMAL(12,2) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_distributor_withdraw_distributor_id (distributor_id),
                    KEY idx_distributor_withdraw_status (status)
                )
                """;
    }

    private Map<String, String> distributorRuleColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("distributor_id", "distributor_id BIGINT NOT NULL");
        columns.put("rule_type", "rule_type VARCHAR(16) NOT NULL");
        columns.put("rule_value", "rule_value DECIMAL(10,4) NOT NULL");
        columns.put("is_active", "is_active TINYINT NOT NULL DEFAULT 1");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private Map<String, String> distributorIncomeDetailColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("distributor_id", "distributor_id BIGINT NOT NULL");
        columns.put("order_id", "order_id BIGINT NOT NULL");
        columns.put("order_amount", "order_amount DECIMAL(12,2) NOT NULL");
        columns.put("income_amount", "income_amount DECIMAL(12,2) NOT NULL");
        columns.put("settlement_id", "settlement_id BIGINT");
        columns.put("settlement_time", "settlement_time DATETIME");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private Map<String, String> distributorSettlementColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("distributor_id", "distributor_id BIGINT NOT NULL");
        columns.put("total_amount", "total_amount DECIMAL(12,2) NOT NULL");
        columns.put("status", "status VARCHAR(16) NOT NULL");
        columns.put("start_time", "start_time DATETIME NOT NULL");
        columns.put("end_time", "end_time DATETIME NOT NULL");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private Map<String, String> distributorWithdrawColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("distributor_id", "distributor_id BIGINT NOT NULL");
        columns.put("amount", "amount DECIMAL(12,2) NOT NULL");
        columns.put("status", "status VARCHAR(16) NOT NULL");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
