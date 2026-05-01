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
public class ServiceFormTemplateSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public ServiceFormTemplateSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        ensureTable("plan_order_service_form_template", templateCreateSql(), templateColumns());
        ensureTable("plan_order_service_form_binding", bindingCreateSql(), bindingColumns());
        ensureTable("plan_order_service_form_template_audit_log", auditCreateSql(), auditColumns());
        ensureDefaultTemplate("PHOTO_CLASSIC", "肖像服务经典版", "到店服务确认单", "肖像摄影", "classic", 1,
                "适合摄影、化妆、选片一体流程");
        ensureDefaultTemplate("MEDICAL_MINI", "医美到店简洁版", "到店项目确认单", "医美咨询", "compact", 1,
                "适合咨询、到店确认与签字留痕");
        ensureDefaultTemplate("CUSTOM_PREMIUM", "门店自定义高级版", "门店服务单", "通用", "premium", 0,
                "适合展示品牌标题和服务步骤");
        ensureDefaultBinding("静安门店", 10L, "PHOTO_CLASSIC", "2026-04-01", 0);
        ensureDefaultBinding("浦东门店", 20L, "MEDICAL_MINI", "2026-04-01", 1);
        ensureDefaultBinding("徐汇门店", 30L, "CUSTOM_PREMIUM", "2026-04-01", 1);
        backfillDefaultBindingStoreIds();
        backfillMissingBindingSnapshots();
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

    private void ensureDefaultTemplate(String templateCode,
                                       String templateName,
                                       String title,
                                       String industry,
                                       String layoutMode,
                                       int recommended,
                                       String description) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM plan_order_service_form_template
                WHERE template_code = ?
                  AND status = 'PUBLISHED'
                """, Integer.class, templateCode);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO plan_order_service_form_template(
                    template_code, template_name, title, industry, layout_mode, designer_engine, config_json,
                    raw_schema_json, normalized_schema_json, recommended, enabled, status, description, create_time, update_time, published_time
                )
                VALUES (?, ?, ?, ?, ?, 'INTERNAL_SCHEMA', ?, ?, ?, ?, 1, 'PUBLISHED', ?, NOW(), NOW(), NOW())
                """, templateCode, templateName, title, industry, layoutMode, defaultConfigJson(layoutMode),
                null, defaultConfigJson(layoutMode), recommended, description);
        log.info("inserted default service form template {}", templateCode);
    }

    private void ensureDefaultBinding(String storeName, Long storeId, String templateCode, String effectiveFrom, int allowOverride) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM plan_order_service_form_binding
                WHERE store_name = ?
                  AND enabled = 1
                """, Integer.class, storeName);
        if (count != null && count > 0) {
            return;
        }
        Long templateId = jdbcTemplate.query("""
                SELECT id
                FROM plan_order_service_form_template
                WHERE template_code = ?
                  AND status = 'PUBLISHED'
                ORDER BY id DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong("id") : null, templateCode);
        if (templateId == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO plan_order_service_form_binding(
                    store_name, store_id, template_id, template_snapshot_json, effective_from,
                    allow_override, enabled, create_time, update_time
                )
                VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW())
                """, storeName, storeId, templateId, defaultTemplateSnapshot(templateId, templateCode), effectiveFrom, allowOverride);
        log.info("inserted default service form binding {} -> {}", storeName, templateCode);
    }

    private void backfillDefaultBindingStoreIds() {
        backfillDefaultBindingStoreId("静安门店", 10L);
        backfillDefaultBindingStoreId("浦东门店", 20L);
        backfillDefaultBindingStoreId("徐汇门店", 30L);
    }

    private void backfillDefaultBindingStoreId(String storeName, Long storeId) {
        jdbcTemplate.update("""
                UPDATE plan_order_service_form_binding
                SET store_id = ?
                WHERE store_name = ?
                  AND store_id IS NULL
                """, storeId, storeName);
    }

    private void backfillMissingBindingSnapshots() {
        int updated = jdbcTemplate.update("""
                UPDATE plan_order_service_form_binding b
                JOIN plan_order_service_form_template t ON t.id = b.template_id
                SET b.template_snapshot_json = JSON_OBJECT(
                        'templateId', t.id,
                        'templateCode', t.template_code,
                        'templateName', t.template_name,
                        'title', t.title,
                        'layoutMode', t.layout_mode,
                        'status', t.status,
                        'publishedTime', DATE_FORMAT(t.published_time, '%Y-%m-%d %H:%i:%s')
                    ),
                    b.update_time = NOW()
                WHERE b.template_snapshot_json IS NULL
                   OR TRIM(b.template_snapshot_json) = ''
                """);
        if (updated > 0) {
            log.info("backfilled {} service form binding snapshots", updated);
        }
    }

    private String defaultTemplateSnapshot(Long templateId, String templateCode) {
        return "{"
                + "\"templateId\":" + templateId
                + ",\"templateCode\":\"" + templateCode + "\""
                + ",\"source\":\"DEFAULT_SEED\""
                + "}";
    }

    private String defaultConfigJson(String layoutMode) {
        return "{"
                + "\"layoutMode\":\"" + layoutMode + "\","
                + "\"density\":\"compact\","
                + "\"sections\":[\"基础信息\",\"服务确认\",\"偏好与补充\",\"纸质签名留位\"],"
                + "\"flexFields\":[{\"label\":\"灵活字段\",\"type\":\"text\",\"required\":false}]"
                + "}";
    }

    private String templateCreateSql() {
        return """
                CREATE TABLE plan_order_service_form_template (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    source_template_id BIGINT,
                    template_code VARCHAR(64) NOT NULL,
                    template_name VARCHAR(100) NOT NULL,
                    title VARCHAR(100) NOT NULL,
                    industry VARCHAR(100),
                    layout_mode VARCHAR(32) NOT NULL,
                    designer_engine VARCHAR(64) DEFAULT 'INTERNAL_SCHEMA',
                    config_json TEXT,
                    raw_schema_json MEDIUMTEXT,
                    normalized_schema_json MEDIUMTEXT,
                    recommended TINYINT DEFAULT 0,
                    enabled TINYINT DEFAULT 0,
                    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
                    description VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    published_time DATETIME,
                    KEY idx_service_form_template_code (template_code),
                    KEY idx_service_form_template_status (status, enabled)
                )
                """;
    }

    private Map<String, String> templateColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("source_template_id", "source_template_id BIGINT");
        columns.put("template_code", "template_code VARCHAR(64) NOT NULL");
        columns.put("template_name", "template_name VARCHAR(100) NOT NULL");
        columns.put("title", "title VARCHAR(100) NOT NULL");
        columns.put("industry", "industry VARCHAR(100)");
        columns.put("layout_mode", "layout_mode VARCHAR(32) NOT NULL");
        columns.put("designer_engine", "designer_engine VARCHAR(64) DEFAULT 'INTERNAL_SCHEMA'");
        columns.put("config_json", "config_json TEXT");
        columns.put("raw_schema_json", "raw_schema_json MEDIUMTEXT");
        columns.put("normalized_schema_json", "normalized_schema_json MEDIUMTEXT");
        columns.put("recommended", "recommended TINYINT DEFAULT 0");
        columns.put("enabled", "enabled TINYINT DEFAULT 0");
        columns.put("status", "status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'");
        columns.put("description", "description VARCHAR(500)");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("update_time", "update_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("published_time", "published_time DATETIME");
        return columns;
    }

    private String bindingCreateSql() {
        return """
                CREATE TABLE plan_order_service_form_binding (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    store_name VARCHAR(100) NOT NULL,
                    store_id BIGINT,
                    template_id BIGINT NOT NULL,
                    template_snapshot_json TEXT,
                    effective_from VARCHAR(32),
                    allow_override TINYINT DEFAULT 0,
                    enabled TINYINT DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_service_form_binding_store (store_name, enabled),
                    KEY idx_service_form_binding_template (template_id)
                )
                """;
    }

    private Map<String, String> bindingColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("store_name", "store_name VARCHAR(100) NOT NULL");
        columns.put("store_id", "store_id BIGINT");
        columns.put("template_id", "template_id BIGINT NOT NULL");
        columns.put("template_snapshot_json", "template_snapshot_json TEXT");
        columns.put("effective_from", "effective_from VARCHAR(32)");
        columns.put("allow_override", "allow_override TINYINT DEFAULT 0");
        columns.put("enabled", "enabled TINYINT DEFAULT 1");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("update_time", "update_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }

    private String auditCreateSql() {
        return """
                CREATE TABLE plan_order_service_form_template_audit_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    target_type VARCHAR(32) NOT NULL,
                    target_id BIGINT,
                    action_type VARCHAR(32) NOT NULL,
                    actor_role_code VARCHAR(64),
                    actor_user_id BIGINT,
                    summary VARCHAR(500),
                    snapshot_json TEXT,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_service_form_audit_target (target_type, target_id),
                    KEY idx_service_form_audit_action (action_type, create_time)
                )
                """;
    }

    private Map<String, String> auditColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("target_type", "target_type VARCHAR(32) NOT NULL");
        columns.put("target_id", "target_id BIGINT");
        columns.put("action_type", "action_type VARCHAR(32) NOT NULL");
        columns.put("actor_role_code", "actor_role_code VARCHAR(64)");
        columns.put("actor_user_id", "actor_user_id BIGINT");
        columns.put("summary", "summary VARCHAR(500)");
        columns.put("snapshot_json", "snapshot_json TEXT");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
