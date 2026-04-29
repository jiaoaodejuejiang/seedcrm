package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerOutboxEventMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulerOutboxServiceImplTest {

    @Mock
    private SchedulerOutboxEventMapper outboxEventMapper;

    @Mock
    private IntegrationProviderConfigMapper providerConfigMapper;

    private SchedulerOutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchedulerOutboxServiceImpl(outboxEventMapper, providerConfigMapper, new ObjectMapper());
    }

    @Test
    void shouldEnqueueDistributionFulfillmentEvent() {
        when(outboxEventMapper.selectOne(any())).thenReturn(null);
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(outboxEventMapper.insert(any(SchedulerOutboxEvent.class))).thenAnswer(invocation -> {
            SchedulerOutboxEvent event = invocation.getArgument(0);
            event.setId(1001L);
            return 1;
        });

        SchedulerOutboxEvent event = service.enqueueFulfillmentEvent(order(), planOrder(), "crm.order.used");

        assertThat(event.getId()).isEqualTo(1001L);
        assertThat(event.getEventType()).isEqualTo("crm.order.used");
        assertThat(event.getProviderCode()).isEqualTo("DISTRIBUTION");
        assertThat(event.getExternalOrderId()).isEqualTo("o_20001");
        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getPayload()).contains("\"eventType\":\"crm.order.used\"");
        assertThat(event.getPayload()).contains("\"externalOrderId\":\"o_20001\"");

        ArgumentCaptor<SchedulerOutboxEvent> captor = ArgumentCaptor.forClass(SchedulerOutboxEvent.class);
        verify(outboxEventMapper).insert(captor.capture());
        assertThat(captor.getValue().getEventKey()).isEqualTo("DISTRIBUTION:crm.order.used:30:3");
    }

    @Test
    void shouldSkipNonExternalOrder() {
        Order order = order();
        order.setExternalPartnerCode(null);
        order.setExternalOrderId(null);

        SchedulerOutboxEvent event = service.enqueueFulfillmentEvent(order, planOrder(), "crm.order.used");

        assertThat(event).isNull();
        verify(outboxEventMapper, never()).insert(any(SchedulerOutboxEvent.class));
    }

    @Test
    void shouldProcessDueEventInMockMode() {
        SchedulerOutboxEvent event = new SchedulerOutboxEvent();
        event.setId(501L);
        event.setProviderCode("DISTRIBUTION");
        event.setEventKey("DISTRIBUTION:crm.order.used:30:3");
        event.setPayload("{\"eventType\":\"crm.order.used\"}");
        event.setStatus("PENDING");
        event.setRetryCount(0);
        when(outboxEventMapper.selectList(any())).thenReturn(List.of(event));
        when(outboxEventMapper.update(any(), any())).thenReturn(1);
        when(outboxEventMapper.selectById(501L)).thenReturn(event, event);
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());

        List<SchedulerOutboxEvent> processed = service.processDue(10);

        assertThat(processed).hasSize(1);
        assertThat(processed.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(processed.get(0).getLastResponse()).contains("outbox event simulated");
    }

    @Test
    void shouldRejectRetryForSuccessfulEvent() {
        SchedulerOutboxEvent event = new SchedulerOutboxEvent();
        event.setId(9L);
        event.setStatus("SUCCESS");
        when(outboxEventMapper.selectById(9L)).thenReturn(event);

        assertThatThrownBy(() -> service.retry(9L, null))
                .isInstanceOf(com.seedcrm.crm.common.exception.BusinessException.class)
                .hasMessageContaining("cannot be retried");
    }

    private Order order() {
        Order order = new Order();
        order.setId(30L);
        order.setExternalPartnerCode("DISTRIBUTION");
        order.setExternalOrderId("o_20001");
        order.setCompleteTime(LocalDateTime.parse("2026-04-29T10:00:00"));
        return order;
    }

    private PlanOrder planOrder() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(3L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        planOrder.setFinishTime(LocalDateTime.parse("2026-04-29T10:00:00"));
        return planOrder;
    }

    private IntegrationProviderConfig distributionProvider() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setProviderCode("DISTRIBUTION");
        provider.setExecutionMode("MOCK");
        provider.setEnabled(1);
        provider.setCallbackUrl("https://distribution.example.test/callback/fulfillment");
        return provider;
    }
}
