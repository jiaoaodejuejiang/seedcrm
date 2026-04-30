package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.scheduler.dto.SchedulerInterfaceDebugRequest;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributionEventDryRunServiceTest {

    @Mock
    private IntegrationProviderConfigMapper providerConfigMapper;

    @Mock
    private IntegrationCallbackEventLogMapper eventLogMapper;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private OrderMapper orderMapper;

    private DistributionEventDryRunService service;

    @BeforeEach
    void setUp() {
        service = new DistributionEventDryRunService(
                providerConfigMapper,
                eventLogMapper,
                customerMapper,
                orderMapper,
                new ObjectMapper());
    }

    @Test
    void shouldPreviewDistributionPaidEventWithoutWritingBusinessTables() {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(eventLogMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = service.dryRun(request());

        assertThat(result).containsEntry("dryRun", true);
        assertThat(result).containsEntry("success", true);
        assertThat(result.get("message")).asString().contains("不写入");

        Map<String, Object> statusMapping = castMap(result.get("statusMapping"));
        assertThat(statusMapping).containsEntry("targetOrderStatus", "PAID_DEPOSIT");
        assertThat(statusMapping).containsEntry("createCustomerAllowed", true);
        assertThat(statusMapping).containsEntry("createOrderAllowed", true);

        Map<String, Object> willWrite = castMap(result.get("willWrite"));
        assertThat(willWrite).containsEntry("clue", false);
        assertThat(willWrite).containsEntry("customer", true);
        assertThat(willWrite).containsEntry("order", true);
        assertThat(willWrite).containsEntry("planOrder", false);
    }

    @Test
    void shouldPreviewDuplicateEvent() {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.selectOne(any())).thenReturn(null);
        IntegrationCallbackEventLog duplicate = new IntegrationCallbackEventLog();
        duplicate.setProcessStatus("SUCCESS");
        when(eventLogMapper.selectOne(any())).thenReturn(duplicate, null);

        Map<String, Object> result = service.dryRun(request());

        Map<String, Object> idempotency = castMap(result.get("orderIdempotency"));
        assertThat(idempotency).containsEntry("eventDuplicate", true);
        assertThat(idempotency).containsEntry("idempotencyResult", "DUPLICATE");
    }

    @Test
    void shouldPreviewDuplicateExternalOrderConflict() {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(customerMapper.selectOne(any())).thenReturn(null);
        Order existingOrder = new Order();
        existingOrder.setId(202L);
        existingOrder.setExternalTradeNo("pay_30001");
        existingOrder.setExternalMemberId("m_10001");
        existingOrder.setType(2);
        existingOrder.setAmount(new BigDecimal("99.00"));
        existingOrder.setStatus("PAID_DEPOSIT");
        when(orderMapper.selectOne(any())).thenReturn(existingOrder);
        when(eventLogMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = service.dryRun(request());

        Map<String, Object> idempotency = castMap(result.get("orderIdempotency"));
        assertThat(idempotency).containsEntry("orderExists", true);
        assertThat(idempotency).containsEntry("idempotencyResult", "EXCEPTION_QUEUED");
        assertThat(idempotency.get("conflicts")).asList().anySatisfy(item ->
                assertThat(item).asString().contains("amount"));
    }

    @Test
    void shouldPreviewDistributionStatusCheckWithoutWritingBusinessTables() {
        IntegrationProviderConfig provider = distributionProvider();
        provider.setStatusQueryPath("/open/distribution/orders/status");
        provider.setStatusMapping("refund_success=distribution.order.refunded");
        when(providerConfigMapper.selectOne(any())).thenReturn(provider);

        Map<String, Object> result = service.dryRun(statusCheckRequest());

        assertThat(result).containsEntry("dryRun", true);
        assertThat(result).containsEntry("success", true);
        assertThat(result.get("message")).asString().contains("不写核心业务表");

        Map<String, Object> schedulerJob = castMap(result.get("schedulerJob"));
        assertThat(schedulerJob).containsEntry("jobCode", "DISTRIBUTION_STATUS_CHECK");
        assertThat(schedulerJob).containsEntry("controllerEndpoint", "/scheduler/distribution/status-check/process");

        Map<String, Object> fieldMapping = castMap(result.get("fieldMapping"));
        assertThat(fieldMapping).containsEntry("externalOrderId", "o_status_001");
        assertThat(fieldMapping).containsEntry("mappedEventType", "distribution.order.refunded");

        Map<String, Object> willExecute = castMap(result.get("willExecute"));
        assertThat(willExecute).containsEntry("directWriteCustomerOrderPlanOrder", false);
        assertThat(willExecute).containsEntry("replayThrough", "DistributionEventIngestService.replayFromScheduler");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private SchedulerInterfaceDebugRequest request() {
        SchedulerInterfaceDebugRequest request = new SchedulerInterfaceDebugRequest();
        request.setMode("MOCK");
        request.setProviderCode("DISTRIBUTION");
        request.setInterfaceCode("DISTRIBUTION_ORDER_PAID");
        request.setPath("/open/distribution/events");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("X-Idempotency-Key", "idem-dry-run-001");
        request.setParameters(parameters);
        request.setPayload("""
                {
                  "eventType": "distribution.order.paid",
                  "eventId": "evt_dry_run_001",
                  "partnerCode": "DISTRIBUTION",
                  "member": {
                    "externalMemberId": "m_10001",
                    "name": "Zhang San",
                    "phone": "13800000000",
                    "role": "member"
                  },
                  "promoter": {
                    "externalPromoterId": "p_90001",
                    "role": "leader"
                  },
                  "order": {
                    "externalOrderId": "o_20001",
                    "externalTradeNo": "pay_30001",
                    "type": "coupon",
                    "amount": 19900,
                    "paidAt": "2026-04-29T09:58:00+08:00",
                    "storeCode": "store_001",
                    "status": "paid"
                  },
                  "rawData": {}
                }
                """);
        return request;
    }

    private SchedulerInterfaceDebugRequest statusCheckRequest() {
        SchedulerInterfaceDebugRequest request = new SchedulerInterfaceDebugRequest();
        request.setMode("MOCK");
        request.setProviderCode("DISTRIBUTION");
        request.setInterfaceCode("DISTRIBUTION_STATUS_CHECK");
        request.setPath("/open/distribution/orders/status");
        request.setPayload("""
                {
                  "externalOrderId": "o_status_001",
                  "status": "refund_success"
                }
                """);
        return request;
    }

    private IntegrationProviderConfig distributionProvider() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DISTRIBUTION");
        provider.setProviderName("External distribution");
        provider.setExecutionMode("MOCK");
        provider.setEnabled(1);
        provider.setEndpointPath("/open/distribution/events");
        return provider;
    }
}
