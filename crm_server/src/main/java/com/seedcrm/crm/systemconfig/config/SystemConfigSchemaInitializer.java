package com.seedcrm.crm.systemconfig.config;

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
                                         @Value("${seedcrm.domain.system-base-url:http://127.0.0.1:4173}") String defaultSystemBaseUrl,
                                         @Value("${seedcrm.domain.api-base-url:http://127.0.0.1:8080}") String defaultApiBaseUrl) {
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
        seedDefault("workflow.system_flow_runtime.enabled", "false", "BOOLEAN", "启用系统流程运行态旁路记录，默认关闭，灰度确认后再开启");
        seedDefault("workflow.service_order.enabled", "false", "BOOLEAN", "服务单正式接管轻量流程引擎开关，默认关闭");
        seedDefault("workflow.scheduling.enabled", "false", "BOOLEAN", "排档正式接管轻量流程引擎开关，默认关闭");
        seedDefault("deposit.direct.enabled", "true", "BOOLEAN", "定金订单允许免码直接核销");
        seedDefault("amount.visibility.store_staff_hidden", "true", "BOOLEAN", "门店角色隐藏前置订单金额");
        seedDefault("form_designer.adapter.enabled", "true", "BOOLEAN", "启用服务单设计器适配层");
        seedDefault("form_designer.provider", "INTERNAL_SCHEMA", "STRING", "默认服务单设计器适配器");
        seedDefault("system.domain.systemBaseUrl", defaultSystemBaseUrl, "URL", "系统后台访问基础域名，用于页面跳转和扫码服务单地址");
        seedDefault("system.domain.apiBaseUrl", defaultApiBaseUrl, "URL", "系统 API 基础域名，用于 Open API、回调接口和三方平台联调地址");
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
}
