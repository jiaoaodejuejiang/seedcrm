package com.seedcrm.crm.scheduler.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SchedulerModuleGuard;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.dto.SchedulerCallbackDebugRequest;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import com.seedcrm.crm.scheduler.dto.SchedulerInterfaceDebugRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerMonitorSummaryResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerQueueActionRequest;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackConfig;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.SchedulerJobAuditLog;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.service.DistributionExceptionRetryService;
import com.seedcrm.crm.scheduler.service.DistributionExceptionService;
import com.seedcrm.crm.scheduler.service.DistributionReconciliationService;
import com.seedcrm.crm.scheduler.service.SchedulerIdempotencyHealthService;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import com.seedcrm.crm.scheduler.service.SchedulerMonitorService;
import com.seedcrm.crm.scheduler.service.SchedulerOutboxService;
import com.seedcrm.crm.scheduler.service.SchedulerService;
import com.seedcrm.crm.scheduler.service.impl.DistributionEventDryRunService;
import com.seedcrm.crm.scheduler.support.SchedulerSensitiveDataMasker;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class SchedulerControllerPartnerScopeTest {

    @Mock
    private SchedulerService schedulerService;

    @Mock
    private SchedulerIntegrationService schedulerIntegrationService;

    @Mock
    private DistributionEventDryRunService distributionEventDryRunService;

    @Mock
    private SchedulerOutboxService schedulerOutboxService;

    @Mock
    private DistributionExceptionService distributionExceptionService;

    @Mock
    private DistributionExceptionRetryService distributionExceptionRetryService;

    @Mock
    private DistributionReconciliationService distributionReconciliationService;

    @Mock
    private SchedulerIdempotencyHealthService schedulerIdempotencyHealthService;

    @Mock
    private SchedulerMonitorService schedulerMonitorService;

    @Mock
    private PermissionRequestContextResolver permissionRequestContextResolver;

    @Mock
    private SchedulerModuleGuard schedulerModuleGuard;

    @Mock
    private SchedulerSensitiveDataMasker schedulerSensitiveDataMasker;

    private SchedulerController controller;

    @BeforeEach
    void setUp() {
        controller = new SchedulerController(
                schedulerService,
                schedulerIntegrationService,
                distributionEventDryRunService,
                schedulerOutboxService,
                distributionExceptionService,
                distributionExceptionRetryService,
                distributionReconciliationService,
                schedulerIdempotencyHealthService,
                schedulerMonitorService,
                permissionRequestContextResolver,
                schedulerModuleGuard,
                schedulerSensitiveDataMasker);
    }

    @Test
    void shouldRejectGlobalSchedulerJobsForPartnerApp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(permissionRequestContextResolver.resolve(request)).thenReturn(partnerContext());

        assertThatThrownBy(() -> controller.listJobs(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global scheduler data");
    }

    @Test
    void shouldFilterProvidersToCurrentPartner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(permissionRequestContextResolver.resolve(request)).thenReturn(partnerContext());
        when(schedulerIntegrationService.listProviders()).thenReturn(List.of(
                provider("DISTRIBUTION"),
                provider("DOUYIN_LAIKE")));

        var response = controller.listProviders(request);

        assertThat(response.getData())
                .extracting(IntegrationProviderConfig::getProviderCode)
                .containsExactly("DISTRIBUTION");
    }

    @Test
    void shouldFilterOutboxEventsToCurrentPartnerBeforeMasking() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PermissionRequestContext context = partnerContext();
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        SchedulerOutboxEvent distributionEvent = outbox("DISTRIBUTION");
        SchedulerOutboxEvent otherEvent = outbox("OTHER_PARTNER");
        when(schedulerOutboxService.list(null)).thenReturn(List.of(distributionEvent, otherEvent));
        when(schedulerSensitiveDataMasker.maskOutboxEvents(List.of(distributionEvent), context))
                .thenReturn(List.of(distributionEvent));

        var response = controller.listOutboxEvents(null, request);

        assertThat(response.getData()).containsExactly(distributionEvent);
    }

    @Test
    void shouldRejectGlobalQueueProcessingForPartnerApp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(permissionRequestContextResolver.resolve(request)).thenReturn(partnerContext());

        assertThatThrownBy(() -> controller.processOutboxEvents(20, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global scheduler operations");
        assertThatThrownBy(() -> controller.processDistributionExceptionRetries(10, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global scheduler operations");
        assertThatThrownBy(() -> controller.processDistributionStatusCheck(20, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global scheduler operations");
        assertThatThrownBy(() -> controller.processDistributionReconciliation(20, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global scheduler operations");
    }

    @Test
    void shouldRejectGlobalDistributionProcessingForIntegrationOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(permissionRequestContextResolver.resolve(request)).thenReturn(operatorContext());

        assertThatThrownBy(() -> controller.processOutboxEvents(20, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global distribution queue processing");
        assertThatThrownBy(() -> controller.processDistributionExceptionRetries(10, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global distribution queue processing");
        assertThatThrownBy(() -> controller.processDistributionStatusCheck(20, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global distribution queue processing");
        assertThatThrownBy(() -> controller.processDistributionReconciliation(20, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global distribution queue processing");
    }

    @Test
    void shouldAllowGlobalDistributionProcessingForIntegrationAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PermissionRequestContext context = adminContext();
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        when(schedulerOutboxService.processDue(20)).thenReturn(List.of());
        when(schedulerSensitiveDataMasker.maskOutboxEvents(List.of(), context)).thenReturn(List.of());
        when(distributionExceptionRetryService.processRetryQueue(10)).thenReturn(List.of());
        when(distributionReconciliationService.checkOrderStatus(20)).thenReturn(List.of());
        when(distributionReconciliationService.pullReconciliation(20)).thenReturn(List.of());

        assertThat(controller.processOutboxEvents(20, request).getData()).isEmpty();
        assertThat(controller.processDistributionExceptionRetries(10, request).getData()).isEmpty();
        assertThat(controller.processDistributionStatusCheck(20, request).getData()).isEmpty();
        assertThat(controller.processDistributionReconciliation(20, request).getData()).isEmpty();
    }

    @Test
    void shouldRejectIdempotencyHealthForPartnerApp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(permissionRequestContextResolver.resolve(request)).thenReturn(partnerContext());

        assertThatThrownBy(() -> controller.inspectIdempotencyHealth("DISTRIBUTION", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("idempotency health");
        assertThatThrownBy(() -> controller.inspectMonitorSummary("DISTRIBUTION", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("idempotency health");
    }

    @Test
    void shouldInspectIdempotencyHealthForBackendIntegrationRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SchedulerIdempotencyHealthResponse health = new SchedulerIdempotencyHealthResponse();
        health.setProviderCode("DISTRIBUTION");
        PermissionRequestContext context = adminContext();
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        when(schedulerIdempotencyHealthService.inspect("DISTRIBUTION")).thenReturn(health);
        when(schedulerSensitiveDataMasker.maskIdempotencyHealth(health, context)).thenReturn(health);

        var response = controller.inspectIdempotencyHealth("DISTRIBUTION", request);

        assertThat(response.getData().getProviderCode()).isEqualTo("DISTRIBUTION");
    }

    @Test
    void shouldMaskIdempotencyHealthForIntegrationOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PermissionRequestContext context = operatorContext();
        SchedulerIdempotencyHealthResponse raw = new SchedulerIdempotencyHealthResponse();
        raw.setProviderCode("DISTRIBUTION");
        SchedulerIdempotencyHealthResponse masked = new SchedulerIdempotencyHealthResponse();
        masked.setProviderCode("DISTRIBUTION");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        when(schedulerIdempotencyHealthService.inspect("DISTRIBUTION")).thenReturn(raw);
        when(schedulerSensitiveDataMasker.maskIdempotencyHealth(raw, context)).thenReturn(masked);

        var response = controller.inspectIdempotencyHealth("DISTRIBUTION", request);

        assertThat(response.getData()).isSameAs(masked);
    }

    @Test
    void shouldInspectMonitorSummaryForBackendIntegrationRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SchedulerMonitorSummaryResponse summary = new SchedulerMonitorSummaryResponse();
        summary.setOverallStatus("HEALTHY");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(adminContext());
        when(schedulerMonitorService.summarize("DISTRIBUTION")).thenReturn(summary);

        var response = controller.inspectMonitorSummary("DISTRIBUTION", request);

        assertThat(response.getData().getOverallStatus()).isEqualTo("HEALTHY");
    }

    @Test
    void shouldRejectGlobalSchedulerMutationForPartnerApp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(permissionRequestContextResolver.resolve(request)).thenReturn(partnerContext());

        assertThatThrownBy(() -> controller.trigger(null, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global scheduler operations");
        assertThatThrownBy(() -> controller.retry("DISTRIBUTION_OUTBOX_PROCESS", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global scheduler operations");
        assertThatThrownBy(() -> controller.retryLog(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global scheduler operations");
    }

    @Test
    void shouldMaskSchedulerLogsForOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PermissionRequestContext context = operatorContext();
        SchedulerJobLog raw = new SchedulerJobLog();
        raw.setPayload("{\"idempotencyKey\":\"idem-001\",\"phone\":\"13800000000\"}");
        SchedulerJobLog masked = new SchedulerJobLog();
        masked.setPayload("{\"idempotencyKey\":\"****\",\"phone\":\"138****0000\"}");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        when(schedulerService.listLogs("DISTRIBUTION_STATUS_CHECK")).thenReturn(List.of(raw));
        when(schedulerSensitiveDataMasker.maskJobLogs(List.of(raw), context)).thenReturn(List.of(masked));

        var response = controller.listLogs("DISTRIBUTION_STATUS_CHECK", request);

        assertThat(response.getData()).containsExactly(masked);
    }

    @Test
    void shouldMaskSchedulerAuditLogsForOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PermissionRequestContext context = operatorContext();
        SchedulerJobAuditLog raw = new SchedulerJobAuditLog();
        raw.setDetail("{\"token\":\"secret-token\",\"phone\":\"13800000000\"}");
        SchedulerJobAuditLog masked = new SchedulerJobAuditLog();
        masked.setDetail("{\"token\":\"****\",\"phone\":\"138****0000\"}");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        when(schedulerService.listAuditLogs("DISTRIBUTION_STATUS_CHECK")).thenReturn(List.of(raw));
        when(schedulerSensitiveDataMasker.maskAuditLogs(List.of(raw), context)).thenReturn(List.of(masked));

        var response = controller.listAuditLogs("DISTRIBUTION_STATUS_CHECK", request);

        assertThat(response.getData()).containsExactly(masked);
    }

    @Test
    void shouldMaskCallbackDebugLatestLogForOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PermissionRequestContext context = operatorContext();
        SchedulerCallbackDebugRequest debugRequest = new SchedulerCallbackDebugRequest();
        debugRequest.setProviderCode("DISTRIBUTION");
        IntegrationProviderConfig provider = provider("DISTRIBUTION");
        IntegrationCallbackEventLog rawLog = new IntegrationCallbackEventLog();
        rawLog.setRequestPayload("{\"phone\":\"13800000000\",\"signature\":\"secret-sign\"}");
        IntegrationCallbackEventLog maskedLog = new IntegrationCallbackEventLog();
        maskedLog.setRequestPayload("{\"phone\":\"138****0000\",\"signature\":\"****\"}");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        when(schedulerIntegrationService.receiveProviderCallback(
                org.mockito.ArgumentMatchers.eq("DISTRIBUTION"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(provider);
        when(schedulerIntegrationService.listCallbackLogs("DISTRIBUTION")).thenReturn(List.of(rawLog));
        when(schedulerSensitiveDataMasker.maskCallbackLogs(List.of(rawLog), context)).thenReturn(List.of(maskedLog));

        var response = controller.debugCallback(debugRequest, request);

        assertThat(response.getData().get("latestLog")).isSameAs(maskedLog);
    }

    @Test
    void shouldRejectExceptionReplayForIntegrationOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SchedulerQueueActionRequest actionRequest = new SchedulerQueueActionRequest();
        actionRequest.setId(12L);
        when(permissionRequestContextResolver.resolve(request)).thenReturn(operatorContext());

        assertThatThrownBy(() -> controller.retryDistributionException(actionRequest, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("replay distribution exceptions");
    }

    @Test
    void shouldRejectPartnerScopedConfigMutationAndCrossPartnerDebug() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(permissionRequestContextResolver.resolve(request)).thenReturn(partnerContext());

        assertThatThrownBy(() -> controller.saveProvider(provider("DISTRIBUTION"), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global scheduler operations");
        assertThatThrownBy(() -> controller.saveCallback(callback("DISTRIBUTION"), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("global scheduler operations");

        SchedulerCallbackDebugRequest callbackDebug = new SchedulerCallbackDebugRequest();
        callbackDebug.setProviderCode("OTHER_PARTNER");
        assertThatThrownBy(() -> controller.debugCallback(callbackDebug, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("another partner scheduler data");

        SchedulerInterfaceDebugRequest interfaceDebug = new SchedulerInterfaceDebugRequest();
        interfaceDebug.setProviderCode("OTHER_PARTNER");
        assertThatThrownBy(() -> controller.debugInterface(interfaceDebug, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("another partner scheduler data");
    }

    @Test
    void shouldMaskDistributionDryRunForIntegrationOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PermissionRequestContext context = operatorContext();
        SchedulerInterfaceDebugRequest debugRequest = new SchedulerInterfaceDebugRequest();
        debugRequest.setProviderCode("DISTRIBUTION");
        Map<String, Object> raw = Map.of(
                "fieldMapping", Map.of(
                        "member", Map.of("phone", "13800000000", "externalMemberId", "member-001"),
                        "order", Map.of("externalTradeNo", "trade-001", "amountYuan", "299.00")));
        Map<String, Object> masked = Map.of(
                "fieldMapping", Map.of(
                        "member", Map.of("phone", "138****0000", "externalMemberId", "me****01"),
                        "order", Map.of("externalTradeNo", "tr****01", "amountYuan", "****")));

        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        when(distributionEventDryRunService.dryRun(debugRequest)).thenReturn(raw);
        when(schedulerSensitiveDataMasker.maskDryRunResult(raw, context)).thenReturn(masked);

        var response = controller.debugInterface(debugRequest, request);

        assertThat(response.getData()).isEqualTo(masked);
    }

    private PermissionRequestContext partnerContext() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("PARTNER_APP");
        context.setDataScope("PARTNER");
        context.setCurrentPartnerCode("DISTRIBUTION");
        context.setResourcePartnerCode("DISTRIBUTION");
        return context;
    }

    private PermissionRequestContext adminContext() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("INTEGRATION_ADMIN");
        context.setDataScope("ALL");
        return context;
    }

    private PermissionRequestContext operatorContext() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("INTEGRATION_OPERATOR");
        context.setDataScope("ALL");
        return context;
    }

    private IntegrationProviderConfig provider(String providerCode) {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setProviderCode(providerCode);
        return provider;
    }

    private IntegrationCallbackConfig callback(String providerCode) {
        IntegrationCallbackConfig callback = new IntegrationCallbackConfig();
        callback.setProviderCode(providerCode);
        return callback;
    }

    private SchedulerOutboxEvent outbox(String partnerCode) {
        SchedulerOutboxEvent event = new SchedulerOutboxEvent();
        event.setProviderCode(partnerCode);
        event.setExternalPartnerCode(partnerCode);
        return event;
    }
}
