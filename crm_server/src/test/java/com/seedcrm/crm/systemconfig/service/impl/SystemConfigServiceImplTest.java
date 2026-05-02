package com.seedcrm.crm.systemconfig.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.support.DistributionOrderTypeMappingResolver;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemConfigServiceImplTest {

    private JdbcTemplate jdbcTemplate;
    private SystemConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:system_config_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.service = new SystemConfigServiceImpl(jdbcTemplate);
        createSchema();
    }

    @Test
    void shouldSaveDomainSettingsWithNormalizedUrlsAndAuditLog() {
        SystemConfigDtos.SaveDomainSettingsRequest request = new SystemConfigDtos.SaveDomainSettingsRequest();
        request.setSystemBaseUrl(" https://crm.seedcrm.test/ ");
        request.setApiBaseUrl("https://api.seedcrm.test/base/");

        SystemConfigDtos.DomainSettingsResponse response = service.saveDomainSettings(request, adminContext());

        assertThat(response.getSystemBaseUrl()).isEqualTo("https://crm.seedcrm.test");
        assertThat(response.getApiBaseUrl()).isEqualTo("https://api.seedcrm.test/base");
        assertThat(response.getEventIngestUrl()).isEqualTo("https://api.seedcrm.test/base/open/distribution/events");
        assertThat(response.getSwaggerUiUrl()).isEqualTo("https://api.seedcrm.test/base/swagger-ui.html");
        assertThat(response.getOpenApiDocsUrl()).isEqualTo("https://api.seedcrm.test/base/v3/api-docs/distribution-open-api");
        assertThat(valueOf("system.domain.systemBaseUrl")).isEqualTo("https://crm.seedcrm.test");
        assertThat(valueOf("system.domain.apiBaseUrl")).isEqualTo("https://api.seedcrm.test/base");

        Integer logCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM system_config_change_log", Integer.class);
        assertThat(logCount).isEqualTo(2);
        List<String> actors = jdbcTemplate.queryForList("SELECT actor_role_code FROM system_config_change_log", String.class);
        assertThat(actors).containsOnly("ADMIN");
    }

    @Test
    void shouldRejectInvalidDomainUrl() {
        SystemConfigDtos.SaveDomainSettingsRequest request = new SystemConfigDtos.SaveDomainSettingsRequest();
        request.setSystemBaseUrl("ftp://crm.seedcrm.test");
        request.setApiBaseUrl("https://api.seedcrm.test");

        assertThatThrownBy(() -> service.saveDomainSettings(request, adminContext()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("http:// 或 https://");
    }

    @Test
    void shouldMaskSensitiveConfigValuesWhenListing() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('wecom.clientSecret', 'super-secret', 'STRING', 'GLOBAL', 'GLOBAL', 1, '企业微信密钥')
                """);

        List<SystemConfigDtos.ConfigResponse> rows = service.listConfigs("wecom.");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getConfigKey()).isEqualTo("wecom.clientSecret");
        assertThat(rows.get(0).getSensitive()).isTrue();
        assertThat(rows.get(0).getConfigValue()).isEqualTo("******");
    }

    @Test
    void shouldRejectUnregisteredConfigKey() {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("unknown.freeForm");
        request.setConfigValue("value");

        assertThatThrownBy(() -> service.saveConfig(request, adminContext()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登记");
    }

    @Test
    void shouldAllowWorkflowConfigAndWriteAuditLog() {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("workflow.service_order.enabled");
        request.setConfigValue("true");
        request.setValueType("BOOLEAN");
        request.setSummary("开启服务单流程灰度");

        SystemConfigDtos.ConfigResponse response = service.saveConfig(request, adminContext());

        assertThat(response.getConfigValue()).isEqualTo("true");
        assertThat(valueOf("workflow.service_order.enabled")).isEqualTo("true");
        String summary = jdbcTemplate.queryForObject("""
                SELECT summary FROM system_config_change_log
                WHERE config_key = 'workflow.service_order.enabled'
                """, String.class);
        assertThat(summary).isEqualTo("开启服务单流程灰度");
    }

    @Test
    void shouldPreviewConfigChangeWithoutWritingAuditLog() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");

        SystemConfigDtos.ConfigPreviewResponse response = service.previewConfig(request);

        assertThat(response.getBeforeValue()).isEqualTo("90");
        assertThat(response.getAfterValue()).isEqualTo("120");
        assertThat(response.getChanged()).isTrue();
        assertThat(response.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(response.getChangeType()).isEqualTo("UPDATE");
        Integer logCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM system_config_change_log", Integer.class);
        assertThat(logCount).isZero();
    }

    @Test
    void shouldListChangeLogsWithSensitiveValuesMasked() {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("wecom.clientSecret");
        request.setConfigValue("new-secret-value");
        request.setValueType("STRING");
        service.saveConfig(request, adminContext());

        List<SystemConfigDtos.ChangeLogResponse> logs = service.listChangeLogs("wecom.", null, 10);

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getConfigKey()).isEqualTo("wecom.clientSecret");
        assertThat(logs.get(0).getSensitive()).isTrue();
        assertThat(logs.get(0).getAfterValue()).isEqualTo("******");
        assertThat(logs.get(0).getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void shouldAllowDistributionOrderTypeMappingJson() {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey(DistributionOrderTypeMappingResolver.CONFIG_KEY);
        request.setConfigValue("""
                {
                  "default": "coupon",
                  "strictProductMapping": true,
                  "aliases": {
                    "deposit": "deposit",
                    "coupon": "coupon"
                  },
                  "rules": [
                    {
                      "ruleId": "sku-001",
                      "externalSkuId": "sku_001",
                      "internalOrderType": "deposit",
                      "priority": 10
                    }
                  ]
                }
                """);
        request.setValueType("JSON");

        SystemConfigDtos.ConfigResponse response = service.saveConfig(request, adminContext());

        assertThat(response.getConfigKey()).isEqualTo(DistributionOrderTypeMappingResolver.CONFIG_KEY);
        assertThat(valueOf(DistributionOrderTypeMappingResolver.CONFIG_KEY)).contains("\"sku_001\"");
    }

    @Test
    void shouldRejectInvalidDistributionOrderTypeMappingJson() {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey(DistributionOrderTypeMappingResolver.CONFIG_KEY);
        request.setConfigValue("""
                {
                  "default": "normal",
                  "aliases": {
                    "coupon": "coupon"
                  }
                }
                """);
        request.setValueType("JSON");

        assertThatThrownBy(() -> service.saveConfig(request, adminContext()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("coupon 或 deposit");
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE system_config (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    config_key VARCHAR(128) NOT NULL,
                    config_value TEXT,
                    value_type VARCHAR(32) NOT NULL DEFAULT 'STRING',
                    scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
                    scope_id VARCHAR(64) NOT NULL DEFAULT 'GLOBAL',
                    enabled TINYINT DEFAULT 1,
                    description VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_system_config_scope_key (scope_type, scope_id, config_key)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE system_config_change_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    config_key VARCHAR(128) NOT NULL,
                    scope_type VARCHAR(32) NOT NULL,
                    scope_id VARCHAR(64) NOT NULL,
                    before_value TEXT,
                    after_value TEXT,
                    actor_role_code VARCHAR(64),
                    actor_user_id BIGINT,
                    summary VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private String valueOf(String key) {
        return jdbcTemplate.queryForObject("""
                SELECT config_value FROM system_config
                WHERE config_key = ? AND scope_type = 'GLOBAL' AND scope_id = 'GLOBAL'
                """, String.class, key);
    }

    private PermissionRequestContext adminContext() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("ADMIN");
        context.setDataScope("ALL");
        context.setCurrentUserId(1L);
        return context;
    }
}
