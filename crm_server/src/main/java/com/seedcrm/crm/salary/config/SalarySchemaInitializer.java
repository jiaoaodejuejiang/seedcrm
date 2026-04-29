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
        ensureTable("salary_settlement_policy", salarySettlementPolicyCreateSql(), salarySettlementPolicyColumns());
        ensureTable("salary_settlement_policy_audit_log", salarySettlementPolicyAuditLogCreateSql(),
                salarySettlementPolicyAuditLogColumns());
        replaceLegacySalaryDetailUniqueIndex();
        ensureIndex("salary_detail", "idx_salary_detail_plan_user_role",
                "CREATE INDEX idx_salary_detail_plan_user_role ON salary_detail(plan_order_id, user_id, role_code)");
        ensureIndex("salary_detail", "idx_salary_detail_refund_record",
                "CREATE INDEX idx_salary_detail_refund_record ON salary_detail(refund_record_id)");
        ensureDefaultRule("CONSULTANT", "PERCENT", "0.0800");
        ensureDefaultRule("DOCTOR", "PERCENT", "0.1200");
        ensureDefaultRule("ASSISTANT", "PERCENT", "0.0500");
        ensureDefaultRule("STORE_SERVICE", "PERCENT", "0.0800");
        ensureDefaultRule("STORE_MANAGER", "PERCENT", "0.1000");
        ensureDefaultRule("PHOTOGRAPHER", "PERCENT", "0.1200");
        ensureDefaultRule("MAKEUP_ARTIST", "PERCENT", "0.0600");
        ensureDefaultRule("PHOTO_SELECTOR", "PERCENT", "0.0700");
        ensureDefaultSettlementPolicy("内部员工按月记账", "INTERNAL_STAFF", "ROLE",
                "ONLINE_CUSTOMER_SERVICE,CLUE_MANAGER,STORE_SERVICE,STORE_MANAGER,PHOTOGRAPHER,MAKEUP_ARTIST,PHOTO_SELECTOR,PRIVATE_DOMAIN_SERVICE",
                null, null, "MONTHLY", "LEDGER_ONLY", null, 10,
                "公司内部员工按月出结算单，只记账不走提现");
        ensureDefaultSettlementPolicy("分销小额自动提现", "DISTRIBUTOR", "AMOUNT",
                "", "0.00", "2999.99", "INSTANT", "WITHDRAW_DIRECT", "3000.00", 20,
                "外部分销小额佣金可随时提现并自动通过");
        ensureDefaultSettlementPolicy("分销大额提现审核", "DISTRIBUTOR", "AMOUNT",
                "", "3000.00", null, "INSTANT", "WITHDRAW_AUDIT", "3000.00", 30,
                "外部分销大额提现必须进入财务审核");
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

    private void replaceLegacySalaryDetailUniqueIndex() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'salary_detail'
                  AND INDEX_NAME = 'uk_salary_detail_plan_user_role'
                """, Integer.class);
        if (count != null && count > 0) {
            jdbcTemplate.execute("DROP INDEX uk_salary_detail_plan_user_role ON salary_detail");
            log.info("dropped legacy unique index uk_salary_detail_plan_user_role on salary_detail");
        }
    }

    private void ensureDefaultRule(String roleCode, String ruleType, String ruleValue) {
        Integer activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM salary_rule
                WHERE role_code = ?
                  AND is_active = 1
                """, Integer.class, roleCode);
        if (activeCount != null && activeCount > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO salary_rule(role_code, rule_type, rule_value, is_active, create_time)
                VALUES (?, ?, ?, 1, NOW())
                """, roleCode, ruleType, ruleValue);
        log.info("inserted default salary rule for role {}", roleCode);
    }

    private void ensureDefaultSettlementPolicy(String policyName,
                                               String subjectType,
                                               String scopeType,
                                               String roleCodes,
                                               String amountMin,
                                               String amountMax,
                                               String settlementCycle,
                                               String settlementMode,
                                               String auditThresholdAmount,
                                               int priority,
                                               String remark) {
        Integer activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM salary_settlement_policy
                WHERE policy_name = ?
                  AND status = 'PUBLISHED'
                """, Integer.class, policyName);
        if (activeCount != null && activeCount > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO salary_settlement_policy(
                    policy_name, subject_type, scope_type, role_codes, amount_min, amount_max,
                    settlement_cycle, settlement_mode, audit_threshold_amount, priority,
                    enabled, status, remark, create_time, update_time, published_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'PUBLISHED', ?, NOW(), NOW(), NOW())
                """, policyName, subjectType, scopeType, roleCodes, amountMin, amountMax, settlementCycle,
                settlementMode, auditThresholdAmount, priority, remark);
        log.info("inserted default salary settlement policy {}", policyName);
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
                    adjustment_type VARCHAR(32),
                    refund_record_id BIGINT,
                    source_salary_detail_id BIGINT,
                    settlement_time DATETIME,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_salary_detail_plan_order_id (plan_order_id),
                    KEY idx_salary_detail_user_id (user_id),
                    KEY idx_salary_detail_settlement_id (settlement_id),
                    KEY idx_salary_detail_refund_record (refund_record_id)
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
        columns.put("adjustment_type", "adjustment_type VARCHAR(32)");
        columns.put("refund_record_id", "refund_record_id BIGINT");
        columns.put("source_salary_detail_id", "source_salary_detail_id BIGINT");
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
                    subject_type VARCHAR(32),
                    settlement_mode VARCHAR(32),
                    audit_required TINYINT DEFAULT 1,
                    audit_remark VARCHAR(500),
                    status VARCHAR(16) NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    approve_time DATETIME,
                    paid_time DATETIME,
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
        columns.put("subject_type", "subject_type VARCHAR(32)");
        columns.put("settlement_mode", "settlement_mode VARCHAR(32)");
        columns.put("audit_required", "audit_required TINYINT DEFAULT 1");
        columns.put("audit_remark", "audit_remark VARCHAR(500)");
        columns.put("status", "status VARCHAR(16) NOT NULL");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("approve_time", "approve_time DATETIME");
        columns.put("paid_time", "paid_time DATETIME");
        return columns;
    }

    private String salarySettlementPolicyCreateSql() {
        return """
                CREATE TABLE salary_settlement_policy (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    source_policy_id BIGINT,
                    policy_name VARCHAR(100) NOT NULL,
                    subject_type VARCHAR(32) NOT NULL,
                    scope_type VARCHAR(32) NOT NULL,
                    role_codes VARCHAR(500),
                    amount_min DECIMAL(12,2),
                    amount_max DECIMAL(12,2),
                    settlement_cycle VARCHAR(32) NOT NULL,
                    settlement_mode VARCHAR(32) NOT NULL,
                    audit_threshold_amount DECIMAL(12,2),
                    priority INT NOT NULL DEFAULT 100,
                    enabled TINYINT NOT NULL DEFAULT 0,
                    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
                    remark VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    published_time DATETIME,
                    KEY idx_salary_policy_subject_status (subject_type, status, enabled),
                    KEY idx_salary_policy_source (source_policy_id)
                )
                """;
    }

    private Map<String, String> salarySettlementPolicyColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("source_policy_id", "source_policy_id BIGINT");
        columns.put("policy_name", "policy_name VARCHAR(100) NOT NULL");
        columns.put("subject_type", "subject_type VARCHAR(32) NOT NULL");
        columns.put("scope_type", "scope_type VARCHAR(32) NOT NULL");
        columns.put("role_codes", "role_codes VARCHAR(500)");
        columns.put("amount_min", "amount_min DECIMAL(12,2)");
        columns.put("amount_max", "amount_max DECIMAL(12,2)");
        columns.put("settlement_cycle", "settlement_cycle VARCHAR(32) NOT NULL");
        columns.put("settlement_mode", "settlement_mode VARCHAR(32) NOT NULL");
        columns.put("audit_threshold_amount", "audit_threshold_amount DECIMAL(12,2)");
        columns.put("priority", "priority INT NOT NULL DEFAULT 100");
        columns.put("enabled", "enabled TINYINT NOT NULL DEFAULT 0");
        columns.put("status", "status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'");
        columns.put("remark", "remark VARCHAR(500)");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("update_time", "update_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("published_time", "published_time DATETIME");
        return columns;
    }

    private String salarySettlementPolicyAuditLogCreateSql() {
        return """
                CREATE TABLE salary_settlement_policy_audit_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    policy_id BIGINT,
                    action_type VARCHAR(32) NOT NULL,
                    actor_role_code VARCHAR(64),
                    actor_user_id BIGINT,
                    summary VARCHAR(500),
                    snapshot_json TEXT,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_salary_policy_audit_policy (policy_id),
                    KEY idx_salary_policy_audit_action (action_type, create_time)
                )
                """;
    }

    private Map<String, String> salarySettlementPolicyAuditLogColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("policy_id", "policy_id BIGINT");
        columns.put("action_type", "action_type VARCHAR(32) NOT NULL");
        columns.put("actor_role_code", "actor_role_code VARCHAR(64)");
        columns.put("actor_user_id", "actor_user_id BIGINT");
        columns.put("summary", "summary VARCHAR(500)");
        columns.put("snapshot_json", "snapshot_json TEXT");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
