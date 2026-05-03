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
    void shouldMaskSensitiveValuesByCapabilityMetadata() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('douyin.appId', 'douyin-app-id-001', 'STRING', 'GLOBAL', 'GLOBAL', 1, '抖音应用 ID')
                """);

        List<SystemConfigDtos.ConfigResponse> rows = service.listConfigs("douyin.");

        assertThat(rows).hasSize(1);
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
    void shouldRejectHighRiskConfigFromLegacyDirectSaveEndpoint() {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("workflow.service_order.enabled");
        request.setConfigValue("true");
        request.setValueType("BOOLEAN");

        assertThatThrownBy(() -> service.saveLegacyConfig(request, adminContext()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配置发布中心");
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

    @Test
    void shouldCreateDraftWithoutMutatingRuntimeConfigOrAuditLog() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");
        request.setSummary("draft dedup window");

        SystemConfigDtos.DraftResponse response = service.createDraft(request, adminContext());

        assertThat(response.getDraftNo()).startsWith("CFG-");
        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getBeforeValue()).isEqualTo("90");
        assertThat(response.getItems().get(0).getAfterValue()).isEqualTo("120");
        assertThat(valueOf("clue.dedup.window_days")).isEqualTo("90");
        Integer logCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM system_config_change_log", Integer.class);
        assertThat(logCount).isZero();
    }

    @Test
    void shouldPublishDraftAndWriteAuditLog() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");
        request.setSummary("publish dedup window");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());
        service.dryRunDraft(draft.getDraftNo(), adminContext());

        SystemConfigDtos.DraftResponse published = service.publishDraft(draft.getDraftNo(), adminContext());

        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(valueOf("clue.dedup.window_days")).isEqualTo("120");
        Integer logCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM system_config_change_log", Integer.class);
        assertThat(logCount).isEqualTo(1);
        String summary = jdbcTemplate.queryForObject("SELECT summary FROM system_config_change_log", String.class);
        assertThat(summary).contains(draft.getDraftNo());
    }

    @Test
    void shouldRequirePassedDryRunBeforePublishingDraft() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());

        assertThatThrownBy(() -> service.publishDraft(draft.getDraftNo(), adminContext()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发布预检查");

        List<SystemConfigDtos.PublishRecordResponse> records = service.listPublishRecords(10);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void shouldRejectDraftPublishWhenRuntimeConfigChangedAfterDraftCreation() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());
        service.dryRunDraft(draft.getDraftNo(), adminContext());
        SystemConfigDtos.SaveConfigRequest directSave = new SystemConfigDtos.SaveConfigRequest();
        directSave.setConfigKey("clue.dedup.window_days");
        directSave.setConfigValue("180");
        directSave.setValueType("NUMBER");
        service.saveConfig(directSave, adminContext());

        assertThatThrownBy(() -> service.publishDraft(draft.getDraftNo(), adminContext()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("运行中配置已变化");
        assertThat(valueOf("clue.dedup.window_days")).isEqualTo("180");
    }

    @Test
    void shouldCreateRollbackDraftWithoutMutatingRuntimeConfig() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest directSave = new SystemConfigDtos.SaveConfigRequest();
        directSave.setConfigKey("clue.dedup.window_days");
        directSave.setConfigValue("120");
        directSave.setValueType("NUMBER");
        service.saveConfig(directSave, adminContext());
        Long logId = jdbcTemplate.queryForObject("SELECT id FROM system_config_change_log", Long.class);

        SystemConfigDtos.ConfigPreviewResponse preview = service.rollbackPreview(logId);
        SystemConfigDtos.DraftResponse rollbackDraft = service.createRollbackDraft(logId, adminContext());

        assertThat(preview.getAfterValue()).isEqualTo("90");
        assertThat(rollbackDraft.getSourceType()).isEqualTo("ROLLBACK");
        assertThat(rollbackDraft.getSourceChangeLogId()).isEqualTo(logId);
        assertThat(rollbackDraft.getItems().get(0).getAfterValue()).isEqualTo("90");
        assertThat(valueOf("clue.dedup.window_days")).isEqualTo("120");
    }

    @Test
    void shouldMaskSensitiveValuesInDraftResponses() {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("wecom.clientSecret");
        request.setConfigValue("super-secret");
        request.setValueType("STRING");

        SystemConfigDtos.DraftResponse response = service.createDraft(request, adminContext());

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getSensitive()).isTrue();
        assertThat(response.getItems().get(0).getAfterValue()).isEqualTo("******");
    }

    @Test
    void shouldValidateAndDryRunDraftAgainstCapabilityRegistry() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());

        SystemConfigDtos.ValidationResponse validation = service.validateDraft(draft.getDraftNo());
        SystemConfigDtos.DryRunResponse dryRun = service.dryRunDraft(draft.getDraftNo());

        assertThat(validation.getValid()).isTrue();
        assertThat(validation.getItems()).hasSize(1);
        assertThat(validation.getItems().get(0).getCapabilityCode()).isEqualTo("CLUE_DEDUP");
        assertThat(validation.getItems().get(0).getStatus()).isEqualTo("PASS");
        assertThat(dryRun.getRunnable()).isTrue();
        assertThat(dryRun.getRuntimeEvents()).anyMatch(item -> item.contains("客资中心"));
        assertThat(service.getDraft(draft.getDraftNo()).getLastDryRunStatus()).isEqualTo("PASS");
        assertThat(valueOf("clue.dedup.window_days")).isEqualTo("90");
    }

    @Test
    void shouldWritePublishRecordAndRuntimeEventsWhenPublishingDraft() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());
        service.dryRunDraft(draft.getDraftNo(), adminContext());

        service.publishDraft(draft.getDraftNo(), adminContext());

        List<SystemConfigDtos.PublishRecordResponse> records = service.listPublishRecords(10);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(records.get(0).getImpactModules()).contains("CLUE");
        SystemConfigDtos.PublishRecordResponse detail = service.getPublishRecord(records.get(0).getPublishNo());
        assertThat(detail.getEvents()).extracting(SystemConfigDtos.RuntimeEventResponse::getEventType)
                .contains("CONFIG_PUBLISHED", "RUNTIME_REFRESH");
        SystemConfigDtos.RuntimeOverviewResponse overview = service.getRuntimeOverview();
        assertThat(overview.getPublishSuccessCount()).isEqualTo(1);
        assertThat(overview.getRuntimeEventPendingCount()).isEqualTo(2);
    }

    @Test
    void shouldBlockPaymentCapabilityPublishAndRecordFailure() {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("payment.gateway.key");
        request.setConfigValue("blocked-secret");
        request.setValueType("STRING");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());

        SystemConfigDtos.ValidationResponse validation = service.validateDraft(draft.getDraftNo());
        service.dryRunDraft(draft.getDraftNo(), adminContext());

        assertThat(validation.getValid()).isFalse();
        assertThat(validation.getItems().get(0).getStatus()).isEqualTo("BLOCK");
        assertThatThrownBy(() -> service.publishDraft(draft.getDraftNo(), adminContext()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("校验未通过");
        List<SystemConfigDtos.PublishRecordResponse> records = service.listPublishRecords(10);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(records.get(0).getFailureReason()).contains("校验");
    }

    @Test
    void shouldMaskSensitiveValuesInPublishRecordSnapshots() {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("wecom.clientSecret");
        request.setConfigValue("super-secret");
        request.setValueType("STRING");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());
        service.dryRunDraft(draft.getDraftNo(), adminContext());

        service.publishDraft(draft.getDraftNo(), adminContext());

        SystemConfigDtos.PublishRecordResponse record = service.listPublishRecords(10).get(0);
        assertThat(record.getAfterSnapshotMaskedJson()).contains("******");
        assertThat(record.getAfterSnapshotMaskedJson()).doesNotContain("super-secret");
    }

    @Test
    void shouldRefreshRuntimeForSuccessfulPublishRecord() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());
        service.dryRunDraft(draft.getDraftNo(), adminContext());
        service.publishDraft(draft.getDraftNo(), adminContext());
        String publishNo = service.listPublishRecords(10).get(0).getPublishNo();

        SystemConfigDtos.PublishRecordResponse refreshed = service.refreshPublishRuntime(publishNo, adminContext());

        assertThat(refreshed.getEvents()).extracting(SystemConfigDtos.RuntimeEventResponse::getEventType)
                .contains("CACHE_EVICT");
    }

    @Test
    void shouldProcessPendingRuntimeEventsForPublishRecord() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());
        service.dryRunDraft(draft.getDraftNo(), adminContext());
        service.publishDraft(draft.getDraftNo(), adminContext());
        String publishNo = service.listPublishRecords(10).get(0).getPublishNo();

        SystemConfigDtos.PublishRecordResponse processed = service.processPublishRuntimeEvents(publishNo, adminContext());

        assertThat(processed.getEvents()).extracting(SystemConfigDtos.RuntimeEventResponse::getStatus)
                .containsOnly("SUCCESS");
        assertThat(processed.getEvents()).allSatisfy(event -> {
            assertThat(event.getHandledAt()).isNotNull();
            assertThat(event.getRetryCount()).isEqualTo(1);
        });
        SystemConfigDtos.RuntimeOverviewResponse overview = service.getRuntimeOverview();
        assertThat(overview.getRuntimeEventPendingCount()).isZero();
        assertThat(overview.getRuntimeEventSuccessCount()).isEqualTo(2);
        assertThat(overview.getLatestRuntimeHandledAt()).isNotNull();
    }

    @Test
    void shouldMarkUnsupportedRuntimeEventFailedWithoutMutatingPublishedConfig() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());
        service.dryRunDraft(draft.getDraftNo(), adminContext());
        service.publishDraft(draft.getDraftNo(), adminContext());
        String publishNo = service.listPublishRecords(10).get(0).getPublishNo();
        jdbcTemplate.update("""
                INSERT INTO system_config_runtime_event(
                    publish_no, module_code, event_type, status, payload_json,
                    retry_count, max_retry_count, create_time
                )
                VALUES (?, 'CLUE', 'BUSINESS_WRITE', 'PENDING', '{}', 0, 3, CURRENT_TIMESTAMP)
                """, publishNo);

        SystemConfigDtos.PublishRecordResponse processed = service.processPublishRuntimeEvents(publishNo, adminContext());

        SystemConfigDtos.RuntimeEventResponse failed = processed.getEvents().stream()
                .filter(event -> "BUSINESS_WRITE".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getErrorMessage()).contains("unsupported runtime event type");
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(valueOf("clue.dedup.window_days")).isEqualTo("120");
    }

    @Test
    void shouldEnqueueManualRuntimeRefreshAndProcessIt() {
        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description)
                VALUES ('clue.dedup.window_days', '90', 'NUMBER', 'GLOBAL', 'GLOBAL', 1, 'dedup window')
                """);
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey("clue.dedup.window_days");
        request.setConfigValue("120");
        request.setValueType("NUMBER");
        SystemConfigDtos.DraftResponse draft = service.createDraft(request, adminContext());
        service.dryRunDraft(draft.getDraftNo(), adminContext());
        service.publishDraft(draft.getDraftNo(), adminContext());
        String publishNo = service.listPublishRecords(10).get(0).getPublishNo();

        service.refreshPublishRuntime(publishNo, adminContext());
        SystemConfigDtos.PublishRecordResponse processed = service.processPublishRuntimeEvents(publishNo, adminContext());

        assertThat(processed.getEvents()).filteredOn(event -> "CACHE_EVICT".equals(event.getEventType()))
                .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo("SUCCESS"));
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
                    change_type VARCHAR(32),
                    risk_level VARCHAR(16),
                    impact_modules_json TEXT,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE system_config_draft (
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
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    published_at DATETIME,
                    discarded_at DATETIME,
                    last_dry_run_hash VARCHAR(64),
                    last_dry_run_status VARCHAR(32),
                    last_dry_run_at DATETIME,
                    last_dry_run_by_role_code VARCHAR(64),
                    last_dry_run_by_user_id BIGINT,
                    UNIQUE KEY uk_system_config_draft_no (draft_no)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE system_config_draft_item (
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
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE system_config_capability (
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
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE system_config_publish_record (
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
                    published_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE system_config_runtime_event (
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
                    handled_at DATETIME
                )
                """);
        seedCapability("SYSTEM_DOMAIN", "system.domain.%", "SYSTEM_SETTING", "URL", "HIGH", 0, "DOMAIN_URL", "MODULE_CALLBACK");
        seedCapability("WORKFLOW_SWITCH", "workflow.%", "SYSTEM_FLOW", "BOOLEAN", "HIGH", 0, "BOOLEAN", "MODULE_CALLBACK");
        seedCapability("CLUE_DEDUP", "clue.dedup.%", "CLUE", "STRING", "MEDIUM", 0, "CLUE_DEDUP", "CACHE_EVICT");
        seedCapability("WECOM_INTEGRATION", "wecom.%", "WECOM", "STRING", "HIGH", 1, "STRING", "MODULE_CALLBACK");
        seedCapability("DOUYIN_INTEGRATION", "douyin.%", "SCHEDULER", "STRING", "HIGH", 1, "STRING", "MODULE_CALLBACK");
        seedCapability("DISTRIBUTION_MAPPING", DistributionOrderTypeMappingResolver.CONFIG_KEY, "SCHEDULER", "JSON", "MEDIUM", 0, "DISTRIBUTION_MAPPING", "MODULE_CALLBACK");
        seedCapability("PAYMENT_BLOCKED", "payment.%", "FINANCE", "STRING", "BLOCKED", 1, "BLOCKED", "NONE");
    }

    private void seedCapability(String code,
                                String pattern,
                                String ownerModule,
                                String valueType,
                                String riskLevel,
                                int sensitive,
                                String validatorCode,
                                String reloadStrategy) {
        jdbcTemplate.update("""
                INSERT INTO system_config_capability(
                    capability_code, config_key_pattern, owner_module, value_type, scope_type_allowed_json,
                    risk_level, sensitive_flag, validator_code, runtime_reload_strategy, enabled
                )
                VALUES (?, ?, ?, ?, '["GLOBAL"]', ?, ?, ?, ?, 1)
                """, code, pattern, ownerModule, valueType, riskLevel, sensitive, validatorCode, reloadStrategy);
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
