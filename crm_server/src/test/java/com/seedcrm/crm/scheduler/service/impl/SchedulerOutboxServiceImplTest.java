package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerOutboxEventMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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
        assertThat(event.getPayload()).contains("\"orderStatus\":\"used\"");
        assertThat(event.getPayload()).contains("\"localOrderStatus\":\"COMPLETED\"");

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
    void shouldRejectNonV1OutboxEventType() {
        assertThatThrownBy(() -> service.enqueueFulfillmentEvent(order(), planOrder(), "crm.order.refunded"))
                .isInstanceOf(com.seedcrm.crm.common.exception.BusinessException.class)
                .hasMessageContaining("crm.order.used");

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
        assertThat(processed.get(0).getLastResponse()).contains("\"traceId\":\"outbox-501\"");
        assertThat(processed.get(0).getLastResponse()).contains("outbox event simulated");
    }

    @Test
    void shouldPushLiveEventWithTraceHeadersAndSignature() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        service = new SchedulerOutboxServiceImpl(outboxEventMapper, providerConfigMapper, new ObjectMapper(), builder.build());

        String payload = "{\"eventType\":\"crm.order.used\"}";
        SchedulerOutboxEvent event = new SchedulerOutboxEvent();
        event.setId(502L);
        event.setProviderCode("DISTRIBUTION");
        event.setEventType("crm.order.used");
        event.setEventKey("DISTRIBUTION:crm.order.used:30:3");
        event.setDestinationUrl("https://distribution.example.test/callback/fulfillment");
        event.setPayload(payload);
        event.setStatus("PENDING");
        event.setRetryCount(0);

        IntegrationProviderConfig provider = distributionProvider();
        provider.setExecutionMode("LIVE");
        provider.setClientSecret("live-secret");
        when(outboxEventMapper.selectList(any())).thenReturn(List.of(event));
        when(outboxEventMapper.update(any(), any())).thenReturn(1);
        when(outboxEventMapper.selectById(502L)).thenReturn(event, event);
        when(providerConfigMapper.selectOne(any())).thenReturn(provider);

        server.expect(requestTo("https://distribution.example.test/callback/fulfillment"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Partner-Code", "DISTRIBUTION"))
                .andExpect(header("X-Idempotency-Key", "DISTRIBUTION:crm.order.used:30:3"))
                .andExpect(header("X-Trace-Id", "outbox-502"))
                .andExpect(header("X-Event-Type", "crm.order.used"))
                .andExpect(content().json(payload))
                .andExpect(request -> {
                    String timestamp = request.getHeaders().getFirst("X-Timestamp");
                    String nonce = request.getHeaders().getFirst("X-Nonce");
                    String signature = request.getHeaders().getFirst("X-Signature");
                    assertThat(OffsetDateTime.parse(timestamp)).isNotNull();
                    assertThat(nonce).isNotBlank();
                    assertThat(signature).isEqualTo(hmacSha256("live-secret",
                            timestamp + "|" + nonce + "|DISTRIBUTION:crm.order.used:30:3|" + payload));
                })
                .andRespond(withSuccess("{\"accepted\":true}", MediaType.APPLICATION_JSON));

        List<SchedulerOutboxEvent> processed = service.processDue(10);

        assertThat(processed).hasSize(1);
        assertThat(processed.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(processed.get(0).getLastResponse()).contains("\"traceId\":\"outbox-502\"");
        assertThat(processed.get(0).getLastResponse()).contains("outbox event pushed");
        server.verify();
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

    @Test
    void shouldRejectPartnerScopedRetryForOtherPartnerOutboxEvent() {
        SchedulerOutboxEvent event = new SchedulerOutboxEvent();
        event.setId(10L);
        event.setStatus("FAILED");
        event.setProviderCode("OTHER_PARTNER");
        event.setExternalPartnerCode("OTHER_PARTNER");
        when(outboxEventMapper.selectById(10L)).thenReturn(event);

        assertThatThrownBy(() -> service.retry(10L, partnerContext()))
                .isInstanceOf(com.seedcrm.crm.common.exception.BusinessException.class)
                .hasMessageContaining("current partner");
    }

    private Order order() {
        Order order = new Order();
        order.setId(30L);
        order.setExternalPartnerCode("DISTRIBUTION");
        order.setExternalOrderId("o_20001");
        order.setStatus(OrderStatus.COMPLETED.name());
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

    private PermissionRequestContext partnerContext() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("PARTNER_APP");
        context.setDataScope("PARTNER");
        context.setCurrentPartnerCode("DISTRIBUTION");
        return context;
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
