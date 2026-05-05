package com.seedcrm.crm.systemconfig.config;

import com.seedcrm.crm.scheduler.support.DistributionOrderTypeMappingResolver;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SystemConfigSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final String defaultSystemBaseUrl;
    private final String defaultApiBaseUrl;

    public SystemConfigSchemaInitializer(DataSource dataSource,
                                         @Value("${seedcrm.domain.system-base-url:http://127.0.0.1:8003}") String defaultSystemBaseUrl,
                                         @Value("${seedcrm.domain.api-base-url:http://127.0.0.1:8004}") String defaultApiBaseUrl) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.defaultSystemBaseUrl = defaultSystemBaseUrl;
        this.defaultApiBaseUrl = defaultApiBaseUrl;
    }

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_config (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    config_key VARCHAR(128) NOT NULL,
                    config_value TEXT,
                    value_type VARCHAR(32) NOT NULL DEFAULT 'STRING',
                    scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
                    scope_id VARCHAR(64) NOT NULL DEFAULT 'GLOBAL',
                    enabled TINYINT DEFAULT 1,
                    description VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_system_config_scope_key (scope_type, scope_id, config_key),
                    KEY idx_system_config_key (config_key, enabled)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_config_change_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    config_key VARCHAR(128) NOT NULL,
                    scope_type VARCHAR(32) NOT NULL,
                    scope_id VARCHAR(64) NOT NULL,
                    before_value TEXT,
                    after_value TEXT,
                    actor_role_code VARCHAR(64),
                    actor_user_id BIGINT,
                    summary VARCHAR(500),
                    change_type VARCHAR(32),
                    risk_level VARCHAR(16),
                    impact_modules_json TEXT,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_system_config_log_key (config_key, create_time)
                )
                """);
        ensureChangeLogSnapshotColumns();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_config_draft (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    draft_no VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
                    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
                    source_change_log_id BIGINT,
                    risk_level VARCHAR(16),
                    impact_modules_json TEXT,
                    created_by_role_code VARCHAR(64),
                    created_by_user_id BIGINT,
                    summary VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    published_at DATETIME,
                    discarded_at DATETIME,
                    last_dry_run_hash VARCHAR(64),
                    last_dry_run_status VARCHAR(32),
                    last_dry_run_at DATETIME,
                    last_dry_run_by_role_code VARCHAR(64),
                    last_dry_run_by_user_id BIGINT,
                    UNIQUE KEY uk_system_config_draft_no (draft_no),
                    KEY idx_system_config_draft_status (status, create_time)
                )
                """);
        ensureDraftDryRunColumns();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_config_draft_item (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    draft_no VARCHAR(64) NOT NULL,
                    config_key VARCHAR(128) NOT NULL,
                    scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
                    scope_id VARCHAR(64) NOT NULL DEFAULT 'GLOBAL',
                    value_type VARCHAR(32) NOT NULL DEFAULT 'STRING',
                    before_value TEXT,
                    after_value TEXT,
                    base_current_value_hash VARCHAR(64),
                    enabled TINYINT DEFAULT 1,
                    description VARCHAR(500),
                    change_type VARCHAR(32),
                    sensitive_flag TINYINT DEFAULT 0,
                    validation_status VARCHAR(32) DEFAULT 'PASS',
                    validation_message VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_system_config_draft_item_no (draft_no),
                    KEY idx_system_config_draft_item_key (config_key)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_config_capability (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    capability_code VARCHAR(64) NOT NULL,
                    config_key_pattern VARCHAR(128) NOT NULL,
                    owner_module VARCHAR(64) NOT NULL,
                    value_type VARCHAR(32) NOT NULL DEFAULT 'STRING',
                    scope_type_allowed_json TEXT,
                    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
                    sensitive_flag TINYINT DEFAULT 0,
                    validator_code VARCHAR(64) NOT NULL DEFAULT 'NONE',
                    runtime_reload_strategy VARCHAR(64) NOT NULL DEFAULT 'NONE',
                    enabled TINYINT DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_system_config_capability_code (capability_code),
                    KEY idx_system_config_capability_pattern (config_key_pattern, enabled)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_config_publish_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    publish_no VARCHAR(64) NOT NULL,
                    draft_no VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    risk_level VARCHAR(16),
                    impact_modules_json TEXT,
                    before_hash VARCHAR(64),
                    after_hash VARCHAR(64),
                    before_snapshot_masked_json TEXT,
                    after_snapshot_masked_json TEXT,
                    validation_result_json TEXT,
                    failure_reason VARCHAR(1000),
                    published_by_role_code VARCHAR(64),
                    published_by_user_id BIGINT,
                    published_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_system_config_publish_no (publish_no),
                    KEY idx_system_config_publish_draft (draft_no),
                    KEY idx_system_config_publish_status (status, published_at)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_config_runtime_event (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    publish_no VARCHAR(64) NOT NULL,
                    module_code VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    payload_json TEXT,
                    error_message VARCHAR(1000),
                    retry_count INT DEFAULT 0,
                    max_retry_count INT DEFAULT 3,
                    next_retry_at DATETIME,
                    locked_by VARCHAR(128),
                    locked_at DATETIME,
                    last_attempt_at DATETIME,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    handled_at DATETIME,
                    KEY idx_system_config_runtime_publish (publish_no),
                    KEY idx_system_config_runtime_status (status, create_time)
                )
                """);
        ensureRuntimeEventColumns();
        seedCapability("SYSTEM_DOMAIN", "system.domain.%", "SYSTEM_SETTING", "URL", "HIGH", false, "DOMAIN_URL", "MODULE_CALLBACK");
        seedCapability("WORKFLOW_SWITCH", "workflow.%", "SYSTEM_FLOW", "BOOLEAN", "HIGH", false, "BOOLEAN", "MODULE_CALLBACK");
        seedCapability("DEPOSIT_DIRECT", "deposit.direct.%", "STORE_SERVICE", "BOOLEAN", "MEDIUM", false, "BOOLEAN", "CACHE_EVICT");
        seedCapability("AMOUNT_VISIBILITY", "amount.visibility.%", "FINANCE", "STRING", "HIGH", false, "FINANCE_VISIBILITY", "CACHE_EVICT");
        seedCapability("FINANCE_LEDGER_BOUNDARY", "finance.ledger.%", "FINANCE", "STRING", "HIGH", false, "FINANCE_LEDGER_BOUNDARY", "CACHE_EVICT");
        seedCapability("CLUE_DEDUP", "clue.dedup.%", "CLUE", "STRING", "MEDIUM", false, "CLUE_DEDUP", "CACHE_EVICT");
        seedCapability("APPOINTMENT_REASON", "appointment.reason.%", "CLUE", "STRING", "MEDIUM", false, "APPOINTMENT_REASON", "CACHE_EVICT");
        seedCapability("STORE_SCHEDULE", "store.schedule.%", "STORE_SERVICE", "JSON", "MEDIUM", false, "STORE_SCHEDULE", "CACHE_EVICT");
        seedCapability("SERVICE_FORM_PRINT_REQUIRED", "service_form.print.required_before_confirm", "PLANORDER", "BOOLEAN", "MEDIUM", false, "BOOLEAN", "CACHE_EVICT");
        seedCapability("SERVICE_FORM_CONFIRM_REQUIRED", "service_form.confirm.required_before_start", "PLANORDER", "BOOLEAN", "MEDIUM", false, "BOOLEAN", "CACHE_EVICT");
        seedCapability("SERVICE_FORM_STALE_POLICY", "service_form.print.stale_policy", "PLANORDER", "STRING", "MEDIUM", false, "SERVICE_FORM_STALE_POLICY", "CACHE_EVICT");
        seedCapability("SERVICE_FORM_DESIGNER", "form_designer.%", "PLANORDER", "STRING", "MEDIUM", false, "FORM_DESIGNER", "MODULE_CALLBACK");
        seedCapability("SERVICE_FORM_DESIGNER_PAPER_SIGNATURE", "form_designer.paper_signature_required", "PLANORDER", "BOOLEAN", "MEDIUM", false, "BOOLEAN", "MODULE_CALLBACK");
        seedCapability("SERVICE_FORM_DESIGNER_SCHEMA_SIZE", "form_designer.max_schema_bytes", "PLANORDER", "NUMBER", "MEDIUM", false, "FORM_DESIGNER_SCHEMA_SIZE", "MODULE_CALLBACK");
        seedCapability("DISTRIBUTION_MAPPING", DistributionOrderTypeMappingResolver.CONFIG_KEY, "SCHEDULER", "JSON", "MEDIUM", false, "DISTRIBUTION_MAPPING", "MODULE_CALLBACK");
        seedCapability("SCHEDULER_INTEGRATION", "scheduler.%", "SCHEDULER", "STRING", "MEDIUM", false, "STRING", "CACHE_EVICT");
        seedCapability("DOUYIN_INTEGRATION", "douyin.%", "SCHEDULER", "STRING", "HIGH", true, "STRING", "MODULE_CALLBACK");
        seedCapability("WECOM_INTEGRATION", "wecom.%", "WECOM", "STRING", "HIGH", true, "STRING", "MODULE_CALLBACK");
        seedCapability("PAYMENT_BLOCKED", "payment.%", "FINANCE", "STRING", "BLOCKED", true, "BLOCKED", "NONE");
        seedDefault("workflow.system_flow_runtime.enabled", "false", "BOOLEAN", "启用系统流程运行态旁路记录，默认关闭，灰度确认后再开启");
        seedDefault("workflow.service_order.enabled", "false", "BOOLEAN", "服务单正式接管轻量流程引擎开关，默认关闭");
        seedDefault("workflow.scheduling.enabled", "false", "BOOLEAN", "排档正式接管轻量流程引擎开关，默认关闭");
        seedDefault("deposit.direct.enabled", "true", "BOOLEAN", "定金订单允许免码直接核销");
        seedDefault("amount.visibility.store_staff_hidden", "true", "BOOLEAN", "门店角色隐藏前置订单金额");
        seedDefault("amount.visibility.store_staff_hidden_roles",
                "STORE_SERVICE,STORE_MANAGER,PHOTOGRAPHER,MAKEUP_ARTIST,PHOTO_SELECTOR",
                "STRING",
                "需要隐藏定金、团购和核销金额的角色编码，英文逗号分隔；服务确认单金额单独配置");
        seedDefault("amount.visibility.service_confirm_hidden_roles",
                "STORE_SERVICE,STORE_MANAGER,PHOTOGRAPHER,MAKEUP_ARTIST,PHOTO_SELECTOR",
                "STRING",
                "需要隐藏服务确认单金额的角色编码，英文逗号分隔；默认门店角色均隐藏");
        seedDefault("amount.visibility.service_confirm_edit_roles",
                "ADMIN,FINANCE",
                "STRING",
                "允许填写或修改服务确认单金额的角色编码，英文逗号分隔；默认仅总部与财务可编辑");
        seedDefault("finance.ledger.only_mode", "true", "BOOLEAN", "财务管理只做台账、对账、线下处理登记，不发起真实收款、退款、提现或转账");
        seedDefault("finance.ledger.refund_salary_reversal_required", "true", "BOOLEAN", "退款流程必须同步生成或登记薪资/分销绩效冲正");
        seedDefault("finance.ledger.distributor_withdraw_register_only", "true", "BOOLEAN", "分销提现只同步外部结果或登记线下处理，不在本系统发起打款");
        seedDefault("clue.dedup.enabled", "true", "BOOLEAN", "客资入库启用按客户身份去重，默认开启");
        seedDefault("clue.dedup.window_days", "90", "NUMBER", "客资去重窗口天数；窗口内同客户保留一条基础客资，多条订单/动作写入客资记录");
        seedDefault("appointment.reason.allowed_codes",
                "CUSTOMER_REQUEST,RESCHEDULE,STORE_ADJUST,TRAFFIC_DELAY,CUSTOMER_CANCEL",
                "STRING",
                "Appointment reason whitelist for customer scheduling actions.");
        seedDefault("appointment.reason.required_actions",
                "",
                "STRING",
                "Appointment actions that must submit an explicit reason code.");
        seedDefault("appointment.reason.default_create",
                "CUSTOMER_REQUEST",
                "STRING",
                "Default reason for first appointment scheduling.");
        seedDefault("appointment.reason.default_change",
                "RESCHEDULE",
                "STRING",
                "Default reason for rescheduling.");
        seedDefault("appointment.reason.default_cancel",
                "CUSTOMER_CANCEL",
                "STRING",
                "Default reason for appointment cancellation.");
        seedDefault("store.schedule.configs", """
                [
                  {"id":1,"storeName":"静安门店","morningStart":"09:00","morningEnd":"12:00","afternoonStart":"13:30","afternoonEnd":"18:00","slotHours":1.5,"remark":"医美咨询与基础皮肤项目排档"},
                  {"id":2,"storeName":"浦东门店","morningStart":"10:00","morningEnd":"13:00","afternoonStart":"14:00","afternoonEnd":"19:00","slotHours":2,"remark":"植发与术前面诊为主"},
                  {"id":3,"storeName":"徐汇门店","morningStart":"09:30","morningEnd":"12:30","afternoonStart":"13:30","afternoonEnd":"18:30","slotHours":1,"remark":"团购客资集中承接"}
                ]
                """, "JSON", "门店档期配置，用于顾客排档和门店日历；按系统配置统一生效");
        seedDefault("service_form.print.required_before_confirm", "true", "BOOLEAN", "服务确认单必须打印当前版本后才能确认纸质单");
        seedDefault("service_form.confirm.required_before_start", "true", "BOOLEAN", "服务开始前必须确认纸质服务确认单");
        seedDefault("service_form.print.stale_policy", "BLOCK_CONFIRM", "STRING", "服务确认单内容变更后的处理策略：BLOCK_CONFIRM/WARN_ONLY");
        seedDefault("form_designer.adapter.enabled", "true", "BOOLEAN", "启用服务单设计器适配层");
        seedDefault("form_designer.provider", "INTERNAL_SCHEMA", "STRING", "默认服务单设计器适配器");
        seedDefault("form_designer.allowed_engines", "INTERNAL_SCHEMA,FORMILY,VFORM3,LOWCODE_ENGINE,JSON_SCHEMA", "STRING", "允许接入的成熟服务单设计器引擎");
        seedDefault("form_designer.blocked_components", "signature,esign,electronicSignature,canvasSignature,html,iframe,script,webview", "STRING", "服务单设计器导入时强制拦截的组件，电子签名和脚本类默认禁止");
        seedDefault("form_designer.max_schema_bytes", "200000", "NUMBER", "服务单设计器 Schema 单模板最大长度");
        seedDefault("form_designer.paper_signature_required", "true", "BOOLEAN", "打印版服务确认单强制保留纸质手写签名位置");
        seedDefault("system.environment.mode", "TEST", "STRING", "系统运行环境：LOCAL/DEV/TEST/STAGING/PROD；清理测试数据会阻断 PROD");
        seedDefault(DistributionOrderTypeMappingResolver.CONFIG_KEY,
                DistributionOrderTypeMappingResolver.DEFAULT_MAPPING_JSON,
                "JSON",
                "分销外部订单类型、商品和 SKU 到内部团购 / 定金的映射配置");
        seedDefault("system.domain.systemBaseUrl", defaultSystemBaseUrl, "URL", "系统后台访问基础域名，用于页面跳转和扫码服务单地址");
        seedDefault("system.domain.apiBaseUrl", defaultApiBaseUrl, "URL", "系统 API 基础域名，用于 Open API、回调接口和三方平台联调地址");
        migrateAmountVisibilityDefaults();
        migrateLegacyLocalDomainDefaults();
    }

    private void ensureChangeLogSnapshotColumns() {
        addColumnIfMissing("system_config_change_log", "change_type", "VARCHAR(32)");
        addColumnIfMissing("system_config_change_log", "risk_level", "VARCHAR(16)");
        addColumnIfMissing("system_config_change_log", "impact_modules_json", "TEXT");
    }

    private void ensureDraftDryRunColumns() {
        addColumnIfMissing("system_config_draft", "last_dry_run_hash", "VARCHAR(64)");
        addColumnIfMissing("system_config_draft", "last_dry_run_status", "VARCHAR(32)");
        addColumnIfMissing("system_config_draft", "last_dry_run_at", "DATETIME");
        addColumnIfMissing("system_config_draft", "last_dry_run_by_role_code", "VARCHAR(64)");
        addColumnIfMissing("system_config_draft", "last_dry_run_by_user_id", "BIGINT");
    }

    private void ensureRuntimeEventColumns() {
        addColumnIfMissing("system_config_runtime_event", "retry_count", "INT DEFAULT 0");
        addColumnIfMissing("system_config_runtime_event", "max_retry_count", "INT DEFAULT 3");
        addColumnIfMissing("system_config_runtime_event", "next_retry_at", "DATETIME");
        addColumnIfMissing("system_config_runtime_event", "locked_by", "VARCHAR(128)");
        addColumnIfMissing("system_config_runtime_event", "locked_at", "DATETIME");
        addColumnIfMissing("system_config_runtime_event", "last_attempt_at", "DATETIME");
    }

    private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        } catch (Exception ignored) {
            // Existing installations may already have the column; CREATE TABLE covers fresh databases.
        }
    }

    private void seedDefault(String key, String value, String valueType, String description) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_config
                WHERE scope_type = 'GLOBAL' AND scope_id = 'GLOBAL' AND config_key = ?
                """, Integer.class, key);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES (?, ?, ?, 'GLOBAL', 'GLOBAL', 1, ?)
                """, key, value, valueType, description);
    }

    private void seedCapability(String code,
                                String pattern,
                                String ownerModule,
                                String valueType,
                                String riskLevel,
                                boolean sensitive,
                                String validatorCode,
                                String reloadStrategy) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_config_capability
                WHERE capability_code = ?
                """, Integer.class, code);
        String scopeTypes = "[\"GLOBAL\"]";
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE system_config_capability
                    SET config_key_pattern = ?,
                        owner_module = ?,
                        value_type = ?,
                        scope_type_allowed_json = ?,
                        risk_level = ?,
                        sensitive_flag = ?,
                        validator_code = ?,
                        runtime_reload_strategy = ?,
                        enabled = 1,
                        update_time = CURRENT_TIMESTAMP
                    WHERE capability_code = ?
                    """, pattern, ownerModule, valueType, scopeTypes, riskLevel,
                    sensitive ? 1 : 0, validatorCode, reloadStrategy, code);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO system_config_capability(
                    capability_code, config_key_pattern, owner_module, value_type, scope_type_allowed_json,
                    risk_level, sensitive_flag, validator_code, runtime_reload_strategy, enabled
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, code, pattern, ownerModule, valueType, scopeTypes, riskLevel,
                sensitive ? 1 : 0, validatorCode, reloadStrategy);
    }

    private void migrateLegacyLocalDomainDefaults() {
        updateLegacyLocalDefault("system.domain.systemBaseUrl", defaultSystemBaseUrl,
                "http://127.0.0.1:4173",
                "http://localhost:4173",
                "http://127.0.0.1:5173",
                "http://localhost:5173");
        updateLegacyLocalDefault("system.domain.apiBaseUrl", defaultApiBaseUrl,
                "http://127.0.0.1:8080",
                "http://localhost:8080");
    }

    private void migrateAmountVisibilityDefaults() {
        updateDefaultValueIfUnchanged(
                "amount.visibility.service_confirm_hidden_roles",
                "STORE_SERVICE,PHOTOGRAPHER,MAKEUP_ARTIST",
                "STORE_SERVICE,STORE_MANAGER,PHOTOGRAPHER,MAKEUP_ARTIST,PHOTO_SELECTOR",
                "需要隐藏服务确认单金额的角色编码，英文逗号分隔；默认门店角色均隐藏");
        updateDefaultValueIfUnchanged(
                "amount.visibility.service_confirm_edit_roles",
                "ADMIN,FINANCE,PHOTO_SELECTOR",
                "ADMIN,FINANCE",
                "允许填写或修改服务确认单金额的角色编码，英文逗号分隔；默认仅总部与财务可编辑");
    }

    private void updateDefaultValueIfUnchanged(String key, String oldValue, String newValue, String description) {
        jdbcTemplate.update("""
                UPDATE system_config
                SET config_value = ?, description = ?, update_time = CURRENT_TIMESTAMP
                WHERE scope_type = 'GLOBAL'
                  AND scope_id = 'GLOBAL'
                  AND config_key = ?
                  AND config_value = ?
                """, newValue, description, key, oldValue);
    }

    private void updateLegacyLocalDefault(String key, String replacement, String... legacyValues) {
        for (String legacyValue : legacyValues) {
            jdbcTemplate.update("""
                    UPDATE system_config
                    SET config_value = ?, update_time = CURRENT_TIMESTAMP
                    WHERE scope_type = 'GLOBAL'
                      AND scope_id = 'GLOBAL'
                      AND config_key = ?
                      AND config_value = ?
                    """, replacement, key, legacyValue);
        }
    }
}
