package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventResponse;
import com.seedcrm.crm.scheduler.dto.DistributionReconciliationDtos.DistributionReconciliationResult;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.service.DistributionEventIngestService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributionReconciliationServiceImplTest {

    @Mock
    private IntegrationProviderConfigMapper providerConfigMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private DistributionEventIngestService distributionEventIngestService;

    private DistributionReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DistributionReconciliationServiceImpl(
                providerConfigMapper,
                orderMapper,
                distributionEventIngestService,
                new ObjectMapper());
    }

    @Test
    void shouldReplayRefundStatusThroughIngestServiceInMockMode() {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(orderMapper.selectList(any())).thenReturn(List.of(distributionOrder("refunded")));
        DistributionEventResponse response = new DistributionEventResponse();
        response.setProcessStatus("SUCCESS");
        response.setMessage("external order status updated");
        when(distributionEventIngestService.replayFromScheduler(any(JsonNode.class), eq("DISTRIBUTION"), any()))
                .thenReturn(response);

        List<DistributionReconciliationResult> results = service.checkOrderStatus(20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAction()).isEqualTo("REPLAYED");
        assertThat(results.get(0).getEventType()).isEqualTo("distribution.order.refunded");
        assertThat(results.get(0).getIdempotencyKey())
                .isEqualTo("DISTRIBUTION:STATUS_CHECK:distribution.order.refunded:dist_order_001");

        ArgumentCaptor<JsonNode> payloadCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(distributionEventIngestService).replayFromScheduler(
                payloadCaptor.capture(),
                eq("DISTRIBUTION"),
                eq("DISTRIBUTION:STATUS_CHECK:distribution.order.refunded:dist_order_001"));
        assertThat(payloadCaptor.getValue().path("eventType").asText()).isEqualTo("distribution.order.refunded");
        assertThat(payloadCaptor.getValue().path("order").path("externalOrderId").asText()).isEqualTo("dist_order_001");
        assertThat(payloadCaptor.getValue().path("rawData").path("jobType").asText()).isEqualTo("STATUS_CHECK");
    }

    @Test
    void shouldSkipPaidStatusWithoutReplayingInLocalStatusCheck() {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(orderMapper.selectList(any())).thenReturn(List.of(distributionOrder("paid")));

        List<DistributionReconciliationResult> results = service.checkOrderStatus(20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAction()).isEqualTo("NO_CHANGE");
        verify(distributionEventIngestService, never()).replayFromScheduler(any(), any(), any());
    }

    @Test
    void shouldUseStatusMappingWhenProviderDefinesCustomExternalStatus() {
        IntegrationProviderConfig provider = distributionProvider();
        provider.setStatusMapping("after_sale_success=distribution.order.refunded");
        when(providerConfigMapper.selectOne(any())).thenReturn(provider);
        Order order = distributionOrder("after_sale_success");
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        DistributionEventResponse response = new DistributionEventResponse();
        response.setProcessStatus("SUCCESS");
        response.setMessage("mapped");
        when(distributionEventIngestService.replayFromScheduler(any(JsonNode.class), eq("DISTRIBUTION"), any()))
                .thenReturn(response);

        List<DistributionReconciliationResult> results = service.pullReconciliation(20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAction()).isEqualTo("REPLAYED");
        assertThat(results.get(0).getEventType()).isEqualTo("distribution.order.refunded");
    }

    @Test
    void shouldRequireSecretBeforeLiveStatusCheckCall() {
        IntegrationProviderConfig provider = distributionProvider();
        provider.setExecutionMode("LIVE");
        provider.setBaseUrl("https://distribution.example.test");
        provider.setStatusQueryPath("/open/distribution/orders/status");
        provider.setClientSecret(null);
        when(providerConfigMapper.selectOne(any())).thenReturn(provider);
        when(orderMapper.selectList(any())).thenReturn(List.of(distributionOrder("refunded")));

        List<DistributionReconciliationResult> results = service.checkOrderStatus(20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAction()).isEqualTo("FAILED");
        assertThat(results.get(0).getMessage()).contains("secret is required");
        verify(distributionEventIngestService, never()).replayFromScheduler(any(), any(), any());
    }

    private IntegrationProviderConfig distributionProvider() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setProviderCode("DISTRIBUTION");
        provider.setProviderName("External distribution");
        provider.setExecutionMode("MOCK");
        provider.setEnabled(1);
        return provider;
    }

    private Order distributionOrder(String mockStatus) {
        Order order = new Order();
        order.setId(101L);
        order.setSource("distribution");
        order.setExternalPartnerCode("DISTRIBUTION");
        order.setExternalOrderId("dist_order_001");
        order.setExternalTradeNo("pay_001");
        order.setExternalMemberId("member_001");
        order.setExternalPromoterId("promoter_001");
        order.setAmount(new BigDecimal("199.00"));
        order.setStatus("PAID_DEPOSIT");
        order.setRawData("""
                {
                  "schedulerMockStatus": "%s",
                  "order": {
                    "externalOrderId": "dist_order_001",
                    "externalTradeNo": "pay_001",
                    "refundAmount": 19900
                  }
                }
                """.formatted(mockStatus));
        return order;
    }
}
