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
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_system_config_log_key (config_key, create_time)
                )
                """);
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
                    UNIQUE KEY uk_system_config_draft_no (draft_no),
                    KEY idx_system_config_draft_status (status, create_time)
                )
                """);
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
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    handled_at DATETIME,
                    KEY idx_system_config_runtime_publish (publish_no),
                    KEY idx_system_config_runtime_status (status, create_time)
                )
                """);
        seedCapability("SYSTEM_DOMAIN", "system.domain.%", "SYSTEM_SETTING", "URL", "HIGH", false, "DOMAIN_URL", "MODULE_CALLBACK");
        seedCapability("WORKFLOW_SWITCH", "workflow.%", "SYSTEM_FLOW", "BOOLEAN", "HIGH", false, "BOOLEAN", "MODULE_CALLBACK");
        seedCapability("DEPOSIT_DIRECT", "deposit.direct.%", "STORE_SERVICE", "BOOLEAN", "MEDIUM", false, "BOOLEAN", "CACHE_EVICT");
        seedCapability("AMOUNT_VISIBILITY", "amount.visibility.%", "FINANCE", "STRING", "HIGH", false, "FINANCE_VISIBILITY", "CACHE_EVICT");
        seedCapability("CLUE_DEDUP", "clue.dedup.%", "CLUE", "STRING", "MEDIUM", false, "CLUE_DEDUP", "CACHE_EVICT");
        seedCapability("SERVICE_FORM_DESIGNER", "form_designer.%", "PLANORDER", "STRING", "MEDIUM", false, "FORM_DESIGNER", "MODULE_CALLBACK");
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
                "STORE_SERVICE,PHOTOGRAPHER,MAKEUP_ARTIST",
                "STRING",
                "需要隐藏服务确认单金额的角色编码，英文逗号分隔；默认店长和选片负责人可看服务确认金额");
        seedDefault("amount.visibility.service_confirm_edit_roles",
                "ADMIN,FINANCE,PHOTO_SELECTOR",
                "STRING",
                "允许填写或修改服务确认单金额的角色编码，英文逗号分隔；默认选片负责人填写，店长只读查看");
        seedDefault("clue.dedup.enabled", "true", "BOOLEAN", "客资入库启用按客户身份去重，默认开启");
        seedDefault("clue.dedup.window_days", "90", "NUMBER", "客资去重窗口天数；窗口内同客户保留一条基础客资，多条订单/动作写入客资记录");
        seedDefault("form_designer.adapter.enabled", "true", "BOOLEAN", "启用服务单设计器适配层");
        seedDefault("form_designer.provider", "INTERNAL_SCHEMA", "STRING", "默认服务单设计器适配器");
        seedDefault("system.environment.mode", "TEST", "STRING", "系统运行环境：LOCAL/DEV/TEST/STAGING/PROD；清理测试数据会阻断 PROD");
        seedDefault(DistributionOrderTypeMappingResolver.CONFIG_KEY,
                DistributionOrderTypeMappingResolver.DEFAULT_MAPPING_JSON,
                "JSON",
                "分销外部订单类型、商品和 SKU 到内部团购 / 定金的映射配置");
        seedDefault("system.domain.systemBaseUrl", defaultSystemBaseUrl, "URL", "系统后台访问基础域名，用于页面跳转和扫码服务单地址");
        seedDefault("system.domain.apiBaseUrl", defaultApiBaseUrl, "URL", "系统 API 基础域名，用于 Open API、回调接口和三方平台联调地址");
        migrateLegacyLocalDomainDefaults();
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
