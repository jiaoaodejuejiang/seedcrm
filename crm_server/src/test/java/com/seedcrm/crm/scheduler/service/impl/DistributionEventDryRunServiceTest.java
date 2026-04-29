package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.scheduler.dto.SchedulerInterfaceDebugRequest;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
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
