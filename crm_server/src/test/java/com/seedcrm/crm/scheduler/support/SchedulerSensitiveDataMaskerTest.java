package com.seedcrm.crm.scheduler.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import java.util.List;
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

    private PermissionRequestContext context(String roleCode) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(roleCode);
        return context;
    }
}
