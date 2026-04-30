package com.seedcrm.crm.scheduler.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.SchedulerJobAuditLog;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchedulerSensitiveDataMaskerTest {

    private SchedulerSensitiveDataMasker masker;

    @BeforeEach
    void setUp() {
        masker = new SchedulerSensitiveDataMasker(new ObjectMapper());
    }

    @Test
    void shouldMaskDistributionExceptionPayloadForOperatorWithoutMutatingSource() {
        DistributionExceptionRecord record = new DistributionExceptionRecord();
        record.setId(7L);
        record.setPhone("13800000000");
        record.setExternalOrderId("ext-order-001");
        record.setRawPayload("""
                {"member":{"phone":"13800000000","name":"customer-a"},"order":{"externalOrderId":"ext-order-001"},"token":"secret-token"}
                """);
        record.setConflictDetailJson("""
                [{"field":"phone","existingValue":"13800000000","incomingValue":"13900001111"}]
                """);

        List<DistributionExceptionRecord> result = masker.maskDistributionExceptions(List.of(record), context("integration_operator"));

        DistributionExceptionRecord masked = result.get(0);
        assertThat(masked).isNotSameAs(record);
        assertThat(masked.getPhone()).isEqualTo("138****0000");
        assertThat(masked.getRawPayload()).doesNotContain("13800000000", "secret-token");
        assertThat(masked.getRawPayload()).contains("138****0000", "ext-order-001");
        assertThat(masked.getConflictDetailJson()).doesNotContain("13800000000", "13900001111");
        assertThat(record.getPhone()).isEqualTo("13800000000");
        assertThat(record.getRawPayload()).contains("secret-token");
    }

    @Test
    void shouldKeepDistributionExceptionPayloadForIntegrationAdmin() {
        DistributionExceptionRecord record = new DistributionExceptionRecord();
        record.setPhone("13800000000");
        record.setRawPayload("{\"member\":{\"phone\":\"13800000000\"}}");

        List<DistributionExceptionRecord> result = masker.maskDistributionExceptions(List.of(record), context("integration_admin"));

        assertThat(result.get(0)).isSameAs(record);
        assertThat(result.get(0).getPhone()).isEqualTo("13800000000");
        assertThat(result.get(0).getRawPayload()).contains("13800000000");
    }

    @Test
    void shouldMaskCallbackLogPayloadForOperator() {
        IntegrationCallbackEventLog log = new IntegrationCallbackEventLog();
        log.setQueryString("{\"phone\":\"13900001111\",\"signature\":\"signature-001\",\"state\":\"ok\"}");
        log.setRequestPayload("{\"buyer_phone\":\"13700002222\",\"data\":{\"access_token\":\"access-token-001\"}}");
        log.setCallbackState("state-phone-13800000000");
        log.setAuthCode("auth-code-001");
        log.setTraceId("trace-001");

        List<IntegrationCallbackEventLog> result = masker.maskCallbackLogs(List.of(log), context("integration_operator"));

        IntegrationCallbackEventLog masked = result.get(0);
        assertThat(masked).isNotSameAs(log);
        assertThat(masked.getQueryString()).doesNotContain("13900001111", "signature-001");
        assertThat(masked.getRequestPayload()).doesNotContain("13700002222", "access-token-001");
        assertThat(masked.getCallbackState()).isEqualTo("state-phone-138****0000");
        assertThat(masked.getAuthCode()).contains("****");
        assertThat(masked.getTraceId()).isEqualTo("trace-001");
        assertThat(log.getRequestPayload()).contains("access-token-001");
    }

    @Test
    void shouldMaskOutboxPayloadAndUrlForOperator() {
        SchedulerOutboxEvent event = new SchedulerOutboxEvent();
        event.setId(11L);
        event.setExternalOrderId("ext-order-009");
        event.setDestinationUrl("https://partner.example.com/callback?token=secret-token&phone=13800000000");
        event.setPayload("""
                {"eventType":"crm.order.used","member":{"phone":"13800000000"},"order":{"externalOrderId":"ext-order-009","amount":"299.00"}}
                """);
        event.setLastResponse("{\"success\":true,\"mobile\":\"13900001111\",\"refund_amount\":\"99.00\"}");
        event.setLastError("partner rejected phone 13700002222");

        List<SchedulerOutboxEvent> result = masker.maskOutboxEvents(List.of(event), context("integration_operator"));

        SchedulerOutboxEvent masked = result.get(0);
        assertThat(masked).isNotSameAs(event);
        assertThat(masked.getExternalOrderId()).isEqualTo("ext-order-009");
        assertThat(masked.getDestinationUrl()).doesNotContain("secret-token", "13800000000");
        assertThat(masked.getPayload()).doesNotContain("13800000000", "299.00");
        assertThat(masked.getPayload()).contains("138****0000", "ext-order-009");
        assertThat(masked.getLastResponse()).doesNotContain("13900001111", "99.00");
        assertThat(masked.getLastError()).contains("137****2222");
        assertThat(event.getPayload()).contains("13800000000", "299.00");
    }

    @Test
    void shouldKeepOutboxPayloadForAdmin() {
        SchedulerOutboxEvent event = new SchedulerOutboxEvent();
        event.setPayload("{\"member\":{\"phone\":\"13800000000\"},\"order\":{\"amount\":\"299.00\"}}");

        SchedulerOutboxEvent result = masker.maskOutboxEvent(event, context("admin"));

        assertThat(result).isSameAs(event);
        assertThat(result.getPayload()).contains("13800000000", "299.00");
    }

    @Test
    void shouldMaskDryRunResultForOperator() {
        Map<String, Object> result = Map.of(
                "fieldMapping", Map.of(
                        "member", Map.of(
                                "name", "张三",
                                "phone", "13800000000",
                                "externalMemberId", "member-001"),
                        "order", Map.of(
                                "externalOrderId", "external-order-001",
                                "externalTradeNo", "trade-001",
                                "amountYuan", "299.00")),
                "signaturePreview", Map.of(
                        "signature", "signature-001",
                        "idempotencyKey", "idem-001"));

        Map<String, Object> masked = masker.maskDryRunResult(result, context("integration_operator"));

        assertThat(masked.toString())
                .doesNotContain("张三", "13800000000", "member-001", "trade-001", "299.00", "signature-001", "idem-001")
                .contains("138****0000", "external-order-001", "****");
        assertThat(result.toString()).contains("张三", "13800000000", "member-001", "trade-001", "299.00");
    }

    @Test
    void shouldKeepDryRunResultForIntegrationAdmin() {
        Map<String, Object> result = Map.of(
                "fieldMapping", Map.of(
                        "member", Map.of("phone", "13800000000", "externalMemberId", "member-001"),
                        "order", Map.of("externalTradeNo", "trade-001", "amountYuan", "299.00")));

        Map<String, Object> masked = masker.maskDryRunResult(result, context("integration_admin"));

        assertThat(masked).isSameAs(result);
        assertThat(masked.toString()).contains("13800000000", "member-001", "trade-001", "299.00");
    }

    @Test
    void shouldMaskSchedulerJobLogsForOperator() {
        SchedulerJobLog log = new SchedulerJobLog();
        log.setId(31L);
        log.setPayload("""
                {"items":[{"externalOrderId":"order-001","idempotencyKey":"idem-001","message":"phone 13800000000 amount 299.00","amountYuan":"299.00"}]}
                """);
        log.setErrorMessage("secret token leaked for 13900001111");

        List<SchedulerJobLog> result = masker.maskJobLogs(List.of(log), context("integration_operator"));

        SchedulerJobLog masked = result.get(0);
        assertThat(masked).isNotSameAs(log);
        assertThat(masked.getPayload()).contains("order-001", "138****0000");
        assertThat(masked.getPayload()).doesNotContain("idem-001", "13800000000", "299.00");
        assertThat(masked.getErrorMessage()).contains("139****1111").doesNotContain("13900001111");
        assertThat(log.getPayload()).contains("idem-001", "13800000000", "299.00");
    }

    @Test
    void shouldMaskSchedulerAuditLogsForOperator() {
        SchedulerJobAuditLog auditLog = new SchedulerJobAuditLog();
        auditLog.setSummary("处理 13800000000");
        auditLog.setDetail("{\"idempotencyKey\":\"idem-001\",\"amountYuan\":\"199.00\",\"externalTradeNo\":\"trade-001\"}");

        List<SchedulerJobAuditLog> result = masker.maskAuditLogs(List.of(auditLog), context("integration_operator"));

        SchedulerJobAuditLog masked = result.get(0);
        assertThat(masked).isNotSameAs(auditLog);
        assertThat(masked.getSummary()).contains("138****0000").doesNotContain("13800000000");
        assertThat(masked.getDetail()).doesNotContain("idem-001", "199.00", "trade-001");
        assertThat(auditLog.getDetail()).contains("idem-001", "199.00", "trade-001");
    }

    private PermissionRequestContext context(String roleCode) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(roleCode);
        return context;
    }
}
