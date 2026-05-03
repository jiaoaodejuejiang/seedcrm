package com.seedcrm.crm.systemconfig.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SchedulerModuleGuard;
import com.seedcrm.crm.permission.support.SettingModuleGuard;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemConfigControllerTest {

    private SystemConfigService systemConfigService;
    private PermissionRequestContextResolver resolver;
    private SettingModuleGuard settingModuleGuard;
    private SchedulerModuleGuard schedulerModuleGuard;
    private SystemConfigController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        systemConfigService = mock(SystemConfigService.class);
        resolver = mock(PermissionRequestContextResolver.class);
        settingModuleGuard = mock(SettingModuleGuard.class);
        schedulerModuleGuard = mock(SchedulerModuleGuard.class);
        controller = new SystemConfigController(systemConfigService, resolver, settingModuleGuard, schedulerModuleGuard);
        request = mock(HttpServletRequest.class);
        when(systemConfigService.getDomainSettings()).thenReturn(domainSettings());
    }

    @Test
    void shouldAllowIntegrationOperatorToReadDomainSettingsThroughSchedulerViewPermission() {
        PermissionRequestContext context = context("INTEGRATION_OPERATOR");
        when(resolver.resolve(request)).thenReturn(context);

        ApiResponse<SystemConfigDtos.DomainSettingsResponse> response = controller.getDomainSettings(request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getApiBaseUrl()).isEqualTo("https://api.seedcrm.test");
        verify(schedulerModuleGuard).checkView(context);
        verify(settingModuleGuard, never()).checkView(context);
    }

    @Test
    void shouldKeepAdminDomainReadOnSettingViewPermission() {
        PermissionRequestContext context = context("ADMIN");
        when(resolver.resolve(request)).thenReturn(context);

        controller.getDomainSettings(request);

        verify(settingModuleGuard).checkView(context);
        verify(schedulerModuleGuard, never()).checkView(context);
    }

    @Test
    void shouldKeepDomainSettingsWriteRestrictedToSettingUpdatePermission() {
        PermissionRequestContext context = context("INTEGRATION_OPERATOR");
        SystemConfigDtos.SaveDomainSettingsRequest body = new SystemConfigDtos.SaveDomainSettingsRequest();
        body.setSystemBaseUrl("https://crm.seedcrm.test");
        body.setApiBaseUrl("https://api.seedcrm.test");
        when(resolver.resolve(request)).thenReturn(context);
        when(systemConfigService.saveDomainSettings(body, context)).thenReturn(domainSettings());

        controller.saveDomainSettings(body, request);

        verify(settingModuleGuard).checkUpdate(context);
        verify(schedulerModuleGuard, never()).checkView(context);
    }

    @Test
    void shouldAllowIntegrationAdminToReadDistributionMappingConfigThroughSchedulerViewPermission() {
        PermissionRequestContext context = context("INTEGRATION_ADMIN");
        when(resolver.resolve(request)).thenReturn(context);

        controller.list("distribution.order.type.", request);

        verify(schedulerModuleGuard).checkView(context);
        verify(settingModuleGuard, never()).checkView(context);
    }

    @Test
    void shouldAllowIntegrationAdminToSaveDistributionMappingConfigThroughSchedulerUpdatePermission() {
        PermissionRequestContext context = context("INTEGRATION_ADMIN");
        SystemConfigDtos.SaveConfigRequest body = new SystemConfigDtos.SaveConfigRequest();
        body.setConfigKey("distribution.order.type.mapping");
        body.setConfigValue("{}");
        when(resolver.resolve(request)).thenReturn(context);

        controller.save(body, request);

        verify(schedulerModuleGuard).checkUpdate(context);
        verify(settingModuleGuard, never()).checkUpdate(context);
        verify(systemConfigService).saveLegacyConfig(body, context);
    }

    @Test
    void shouldKeepIntegrationOperatorDistributionMappingWriteDeniedBySettingUpdatePermission() {
        PermissionRequestContext context = context("INTEGRATION_OPERATOR");
        SystemConfigDtos.SaveConfigRequest body = new SystemConfigDtos.SaveConfigRequest();
        body.setConfigKey("distribution.order.type.mapping");
        body.setConfigValue("{}");
        when(resolver.resolve(request)).thenReturn(context);

        controller.save(body, request);

        verify(settingModuleGuard).checkUpdate(context);
        verify(schedulerModuleGuard, never()).checkUpdate(context);
    }

    @Test
    void shouldUseConfigDraftPermissionForConfigPreview() {
        PermissionRequestContext context = context("ADMIN");
        SystemConfigDtos.SaveConfigRequest body = new SystemConfigDtos.SaveConfigRequest();
        body.setConfigKey("clue.dedup.window_days");
        body.setConfigValue("90");
        when(resolver.resolve(request)).thenReturn(context);

        controller.preview(body, request);

        verify(settingModuleGuard).checkConfigDraft(context);
        verify(systemConfigService).previewConfig(body);
    }

    @Test
    void shouldAllowIntegrationAdminToReadDistributionConfigChangeLogsThroughSchedulerViewPermission() {
        PermissionRequestContext context = context("INTEGRATION_ADMIN");
        when(resolver.resolve(request)).thenReturn(context);

        controller.changeLogs("distribution.order.type.", null, 50, request);

        verify(schedulerModuleGuard).checkView(context);
        verify(settingModuleGuard, never()).checkView(context);
        verify(systemConfigService).listChangeLogs("distribution.order.type.", null, 50);
    }

    @Test
    void shouldUseConfigAuditPermissionForDraftList() {
        PermissionRequestContext context = context("ADMIN");
        when(resolver.resolve(request)).thenReturn(context);

        controller.drafts("DRAFT", 50, request);

        verify(settingModuleGuard).checkConfigAudit(context);
        verify(systemConfigService).listDrafts("DRAFT", 50);
    }

    @Test
    void shouldUseConfigDraftPermissionForDraftCreation() {
        PermissionRequestContext context = context("ADMIN");
        SystemConfigDtos.SaveConfigRequest body = new SystemConfigDtos.SaveConfigRequest();
        body.setConfigKey("clue.dedup.window_days");
        body.setConfigValue("120");
        when(resolver.resolve(request)).thenReturn(context);

        controller.createDraft(body, request);

        verify(settingModuleGuard).checkConfigDraft(context);
        verify(systemConfigService).createDraft(body, context);
    }

    @Test
    void shouldUseDedicatedPermissionsForDraftPublishAndRollbackDraft() {
        PermissionRequestContext context = context("ADMIN");
        when(resolver.resolve(request)).thenReturn(context);

        controller.publishDraft("CFG-001", request);
        controller.createRollbackDraft(10L, request);

        verify(settingModuleGuard).checkConfigPublish(context);
        verify(settingModuleGuard).checkConfigRollback(context);
        verify(systemConfigService).publishDraft("CFG-001", context);
        verify(systemConfigService).createRollbackDraft(10L, context);
    }

    @Test
    void shouldUseConfigAuditPermissionForCapabilityAndPublishRecordReads() {
        PermissionRequestContext context = context("ADMIN");
        when(resolver.resolve(request)).thenReturn(context);

        controller.capabilities(request);
        controller.runtimeOverview(request);
        controller.publishRecords(20, request);
        controller.publishRecord("PUB-001", request);

        verify(settingModuleGuard, org.mockito.Mockito.times(4)).checkConfigAudit(context);
        verify(systemConfigService).listCapabilities();
        verify(systemConfigService).getRuntimeOverview();
        verify(systemConfigService).listPublishRecords(20);
        verify(systemConfigService).getPublishRecord("PUB-001");
    }

    @Test
    void shouldUseConfigDraftPermissionForValidateAndDryRun() {
        PermissionRequestContext context = context("ADMIN");
        when(resolver.resolve(request)).thenReturn(context);

        controller.validateDraft("CFG-001", request);
        controller.dryRunDraft("CFG-001", request);

        verify(settingModuleGuard, org.mockito.Mockito.times(2)).checkConfigDraft(context);
        verify(systemConfigService).validateDraft("CFG-001");
        verify(systemConfigService).dryRunDraft("CFG-001");
    }

    @Test
    void shouldUseConfigPublishPermissionForRuntimeRefresh() {
        PermissionRequestContext context = context("ADMIN");
        when(resolver.resolve(request)).thenReturn(context);

        controller.refreshRuntime("PUB-001", request);

        verify(settingModuleGuard).checkConfigPublish(context);
        verify(systemConfigService).refreshPublishRuntime("PUB-001", context);
    }

    private PermissionRequestContext context(String roleCode) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(roleCode);
        context.setDataScope("ALL");
        context.setCurrentUserId(1L);
        return context;
    }

    private SystemConfigDtos.DomainSettingsResponse domainSettings() {
        SystemConfigDtos.DomainSettingsResponse response = new SystemConfigDtos.DomainSettingsResponse();
        response.setSystemBaseUrl("https://crm.seedcrm.test");
        response.setApiBaseUrl("https://api.seedcrm.test");
        response.setEventIngestUrl("https://api.seedcrm.test/open/distribution/events");
        response.setSwaggerUiUrl("https://api.seedcrm.test/swagger-ui.html");
        response.setOpenApiDocsUrl("https://api.seedcrm.test/v3/api-docs/distribution-open-api");
        return response;
    }
}
