package com.seedcrm.crm.finance.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FinanceSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public FinanceSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        ensureTable("account", createAccountSql(), accountColumns());
        ensureTable("ledger", createLedgerSql(), ledgerColumns());
        ensureTable("finance_check_record", createFinanceCheckRecordSql(), financeCheckRecordColumns());
        ensureIndex("account", "uk_account_owner",
                "CREATE UNIQUE INDEX uk_account_owner ON account(owner_type, owner_id)");
        dropIndexIfExists("ledger", "uk_ledger_account_biz");
        ensureIndex("ledger", "uk_ledger_biz_account",
                "CREATE UNIQUE INDEX uk_ledger_biz_account ON ledger(biz_type, biz_id, account_id)");
        ensureIndex("ledger", "idx_ledger_biz_type_biz_id",
                "CREATE INDEX idx_ledger_biz_type_biz_id ON ledger(biz_type, biz_id)");
        ensureIndex("ledger", "idx_ledger_account_id",
                "CREATE INDEX idx_ledger_account_id ON ledger(account_id)");
        ensureIndex("finance_check_record", "idx_finance_check_record_biz",
                "CREATE INDEX idx_finance_check_record_biz ON finance_check_record(biz_type, biz_id)");
        ensureLedgerImmutableTrigger("trg_ledger_before_update", """
                CREATE TRIGGER trg_ledger_before_update
                BEFORE UPDATE ON ledger
                FOR EACH ROW
                SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'ledger is immutable'
                """);
        ensureLedgerImmutableTrigger("trg_ledger_before_delete", """
                CREATE TRIGGER trg_ledger_before_delete
                BEFORE DELETE ON ledger
                FOR EACH ROW
                SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'ledger is immutable'
                """);
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

    private void ensureLedgerImmutableTrigger(String triggerName, String createTriggerSql) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.TRIGGERS
                WHERE TRIGGER_SCHEMA = DATABASE()
                  AND TRIGGER_NAME = ?
                """, Integer.class, triggerName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(createTriggerSql);
            log.info("created trigger {}", triggerName);
        }
    }

    private String createAccountSql() {
        return """
                CREATE TABLE account (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    owner_type VARCHAR(32) NOT NULL,
                    owner_id BIGINT NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """;
    }

    private Map<String, String> accountColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("owner_type", "owner_type VARCHAR(32) NOT NULL");
        columns.put("owner_id", "owner_id BIGINT NOT NULL");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private String createLedgerSql() {
        return """
                CREATE TABLE ledger (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    account_id BIGINT NOT NULL,
                    change_amount DECIMAL(12,2) NOT NULL,
                    balance_after DECIMAL(12,2),
                    biz_type VARCHAR(32) NOT NULL,
                    biz_id BIGINT NOT NULL,
                    direction VARCHAR(16) NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """;
    }

    private Map<String, String> ledgerColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("account_id", "account_id BIGINT NOT NULL");
        columns.put("change_amount", "change_amount DECIMAL(12,2) NOT NULL");
        columns.put("balance_after", "balance_after DECIMAL(12,2)");
        columns.put("biz_type", "biz_type VARCHAR(32) NOT NULL");
        columns.put("biz_id", "biz_id BIGINT NOT NULL");
        columns.put("direction", "direction VARCHAR(16) NOT NULL");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private String createFinanceCheckRecordSql() {
        return """
                CREATE TABLE finance_check_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    biz_type VARCHAR(32) NOT NULL,
                    biz_id BIGINT NOT NULL,
                    expected_amount DECIMAL(12,2) NOT NULL,
                    actual_amount DECIMAL(12,2) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """;
    }

    private Map<String, String> financeCheckRecordColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("biz_type", "biz_type VARCHAR(32) NOT NULL");
        columns.put("biz_id", "biz_id BIGINT NOT NULL");
        columns.put("expected_amount", "expected_amount DECIMAL(12,2) NOT NULL");
        columns.put("actual_amount", "actual_amount DECIMAL(12,2) NOT NULL");
        columns.put("status", "status VARCHAR(16) NOT NULL");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
