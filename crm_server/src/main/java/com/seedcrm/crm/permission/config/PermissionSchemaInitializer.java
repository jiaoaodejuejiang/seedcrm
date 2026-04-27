package com.seedcrm.crm.permission.config;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PermissionSchemaInitializer {

    private static final String TABLE_NAME = "permission_policy";
    private static final String UNIQUE_INDEX = "uk_permission_policy_rule";

    private final JdbcTemplate jdbcTemplate;

    public PermissionSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        if (!tableExists()) {
            jdbcTemplate.execute(createTableSql());
            log.info("created table {}", TABLE_NAME);
        } else {
            ensureMissingColumns();
            ensureUniqueIndex();
        }
        seedDefaults();
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

    private void ensureUniqueIndex() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, UNIQUE_INDEX);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME
                    + " ADD UNIQUE KEY " + UNIQUE_INDEX + " (module_code, action_code, role_code, data_scope)");
            log.info("added unique index {} on {}", UNIQUE_INDEX, TABLE_NAME);
        }
    }

    private String createTableSql() {
        return """
                CREATE TABLE permission_policy (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    module_code VARCHAR(64) NOT NULL,
                    action_code VARCHAR(64) NOT NULL,
                    role_code VARCHAR(64) NOT NULL,
                    data_scope VARCHAR(32) NOT NULL,
                    condition_rule VARCHAR(255),
                    is_enabled TINYINT DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_permission_policy_rule (module_code, action_code, role_code, data_scope)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("module_code", "module_code VARCHAR(64) NOT NULL");
        columns.put("action_code", "action_code VARCHAR(64) NOT NULL");
        columns.put("role_code", "role_code VARCHAR(64) NOT NULL");
        columns.put("data_scope", "data_scope VARCHAR(32) NOT NULL");
        columns.put("condition_rule", "condition_rule VARCHAR(255)");
        columns.put("is_enabled", "is_enabled TINYINT DEFAULT 1");
        columns.put("created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }

    private void seedDefaults() {
        seedPolicy("CLUE", "VIEW", "ONLINE_CUSTOMER_SERVICE", "SELF", null);
        seedPolicy("CLUE", "VIEW", "ONLINE_CUSTOMER_SERVICE", "TEAM", null);
        seedPolicy("CLUE", "VIEW", "CLUE_MANAGER", "ALL", null);
        seedPolicy("CLUE", "CREATE", "CLUE_MANAGER", "ALL", "manual or auto intake");
        seedPolicy("CLUE", "ASSIGN", "CLUE_MANAGER", "ALL", "clue assign");
        seedPolicy("CLUE", "RECYCLE", "CLUE_MANAGER", "ALL", "clue recycle");

        seedPolicy("ORDER", "VIEW", "ONLINE_CUSTOMER_SERVICE", "TEAM", "appointment follow-up");
        seedPolicy("ORDER", "UPDATE", "ONLINE_CUSTOMER_SERVICE", "TEAM", "appointment follow-up");
        seedPolicy("ORDER", "VIEW", "CLUE_MANAGER", "ALL", "appointment scheduling");
        seedPolicy("ORDER", "UPDATE", "CLUE_MANAGER", "ALL", "appointment scheduling");
        seedPolicy("ORDER", "VIEW", "STORE_SERVICE", "STORE", null);
        seedPolicy("ORDER", "UPDATE", "STORE_SERVICE", "STORE", null);
        seedPolicy("ORDER", "REFUND_STORE", "STORE_SERVICE", "STORE", "offline service refund only");
        seedPolicy("ORDER", "FINISH", "STORE_SERVICE", "STORE", "order(status=finished)");
        seedPolicy("ORDER", "VIEW", "STORE_MANAGER", "STORE", null);
        seedPolicy("ORDER", "UPDATE", "STORE_MANAGER", "STORE", null);
        seedPolicy("ORDER", "REFUND_STORE", "STORE_MANAGER", "STORE", "offline service refund only");
        seedPolicy("ORDER", "FINISH", "STORE_MANAGER", "STORE", "order(status=finished)");
        seedPolicy("ORDER", "VIEW", "PHOTOGRAPHER", "STORE", null);
        seedPolicy("ORDER", "UPDATE", "PHOTOGRAPHER", "STORE", null);
        seedPolicy("ORDER", "REFUND_STORE", "PHOTOGRAPHER", "STORE", "offline service refund only");
        seedPolicy("ORDER", "VIEW", "MAKEUP_ARTIST", "STORE", null);
        seedPolicy("ORDER", "UPDATE", "MAKEUP_ARTIST", "STORE", null);
        seedPolicy("ORDER", "REFUND_STORE", "MAKEUP_ARTIST", "STORE", "offline service refund only");
        seedPolicy("ORDER", "VIEW", "PHOTO_SELECTOR", "STORE", null);
        seedPolicy("ORDER", "UPDATE", "PHOTO_SELECTOR", "STORE", null);
        seedPolicy("ORDER", "REFUND_STORE", "PHOTO_SELECTOR", "STORE", "offline service refund only");

        seedPolicy("PLANORDER", "CREATE", "STORE_SERVICE", "STORE", null);
        seedPolicy("PLANORDER", "VIEW", "STORE_SERVICE", "STORE", null);
        seedPolicy("PLANORDER", "UPDATE", "STORE_SERVICE", "STORE", null);
        seedPolicy("PLANORDER", "ASSIGN_ROLE", "STORE_SERVICE", "STORE", null);
        seedPolicy("PLANORDER", "CREATE", "STORE_MANAGER", "STORE", null);
        seedPolicy("PLANORDER", "VIEW", "STORE_MANAGER", "STORE", null);
        seedPolicy("PLANORDER", "UPDATE", "STORE_MANAGER", "STORE", null);
        seedPolicy("PLANORDER", "ASSIGN_ROLE", "STORE_MANAGER", "STORE", null);
        seedPolicy("PLANORDER", "VIEW", "PHOTOGRAPHER", "STORE", null);
        seedPolicy("PLANORDER", "UPDATE", "PHOTOGRAPHER", "STORE", null);
        seedPolicy("PLANORDER", "VIEW", "MAKEUP_ARTIST", "STORE", null);
        seedPolicy("PLANORDER", "UPDATE", "MAKEUP_ARTIST", "STORE", null);
        seedPolicy("PLANORDER", "VIEW", "PHOTO_SELECTOR", "STORE", null);
        seedPolicy("PLANORDER", "UPDATE", "PHOTO_SELECTOR", "STORE", null);

        seedPolicy("PLANORDER", "VIEW", "PRIVATE_DOMAIN_SERVICE", "SELF", "bound customer");
        seedPolicy("ORDER", "VIEW", "PRIVATE_DOMAIN_SERVICE", "SELF", "bound customer");
        seedPolicy("ORDER", "VIEW", "FINANCE", "ALL", "verified and finished order finance scope");
        seedPolicy("ORDER", "UPDATE", "FINANCE", "ALL", "verified payment refund registration");
        seedPolicy("ORDER", "REFUND_PAYMENT", "FINANCE", "ALL", "verified payment refund registration");
        seedPolicy("ORDER", "FINISH", "FINANCE", "ALL", "order(status=finished)");

        seedPolicy("SCHEDULER", "VIEW", "CLUE_MANAGER", "ALL", "scheduler monitor");
        seedPolicy("SCHEDULER", "UPDATE", "CLUE_MANAGER", "ALL", "scheduler config");
        seedPolicy("SCHEDULER", "TRIGGER", "CLUE_MANAGER", "ALL", "scheduler trigger");
        seedPolicy("WECOM", "VIEW", "PRIVATE_DOMAIN_SERVICE", "ALL", "wecom workspace");
        seedPolicy("WECOM", "UPDATE", "PRIVATE_DOMAIN_SERVICE", "ALL", "wecom workspace");

        seedPolicy("PERMISSION", "VIEW", "ADMIN", "ALL", null);
        seedPolicy("PERMISSION", "UPDATE", "ADMIN", "ALL", null);
        seedPolicy("PERMISSION", "CHECK", "ADMIN", "ALL", null);
    }

    private void seedPolicy(String moduleCode,
                            String actionCode,
                            String roleCode,
                            String dataScope,
                            String conditionRule) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM permission_policy
                WHERE module_code = ?
                  AND action_code = ?
                  AND role_code = ?
                  AND data_scope = ?
                """, Integer.class, moduleCode, actionCode, roleCode, dataScope);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO permission_policy(module_code, action_code, role_code, data_scope, condition_rule, is_enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                """, moduleCode, actionCode, roleCode, dataScope, conditionRule, LocalDateTime.now(), LocalDateTime.now());
    }
}
