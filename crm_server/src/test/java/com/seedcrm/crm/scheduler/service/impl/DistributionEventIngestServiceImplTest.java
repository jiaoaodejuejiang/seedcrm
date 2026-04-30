package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventResponse;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.service.DistributionExceptionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributionEventIngestServiceImplTest {

    @Mock
    private IntegrationProviderConfigMapper providerConfigMapper;

    @Mock
    private IntegrationCallbackEventLogMapper eventLogMapper;

    @Mock
    private DistributionEventLogWriter eventLogWriter;

    @Mock
    private DistributionExceptionService distributionExceptionService;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private OrderMapper orderMapper;

    private ObjectMapper objectMapper;
    private DistributionEventIngestServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new DistributionEventIngestServiceImpl(
                providerConfigMapper,
                eventLogMapper,
                eventLogWriter,
                distributionExceptionService,
                customerMapper,
                orderMapper,
                objectMapper);
    }

    @Test
    void shouldCreateCustomerAndPaidOrderForDistributionPaidEvent() throws Exception {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(eventLogMapper.selectOne(any())).thenReturn(null);
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(customerMapper.insert(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(101L);
            return 1;
        });
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(202L);
            return 1;
        });

        DistributionEventResponse response = service.ingest(readPayload(), request("idem-001"));

        assertThat(response.getIdempotencyResult()).isEqualTo("CREATED");
        assertThat(response.getCustomerId()).isEqualTo(101L);
        assertThat(response.getOrderId()).isEqualTo(202L);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerMapper).insert(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getSource()).isEqualTo("distribution");
        assertThat(customerCaptor.getValue().getSourceChannel()).isEqualTo("DISTRIBUTOR");
        assertThat(customerCaptor.getValue().getExternalMemberId()).isEqualTo("m_10001");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getClueId()).isNull();
        assertThat(orderCaptor.getValue().getCustomerId()).isEqualTo(101L);
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo("PAID_DEPOSIT");
        assertThat(orderCaptor.getValue().getExternalOrderId()).isEqualTo("o_20001");

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(eventLogWriter).write(logCaptor.capture());
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("SUCCESS");
        assertThat(logCaptor.getValue().getIdempotencyStatus()).isEqualTo("CREATED");
        assertThat(logCaptor.getValue().getSignatureStatus()).isEqualTo("MOCK_SKIPPED");
    }

    @Test
    void shouldIgnoreDuplicateSuccessfulEventWithoutCreatingAnotherOrder() throws Exception {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        IntegrationCallbackEventLog duplicate = new IntegrationCallbackEventLog();
        duplicate.setProcessStatus("SUCCESS");
        duplicate.setRelatedCustomerId(101L);
        duplicate.setRelatedOrderId(202L);
        when(eventLogMapper.selectOne(any())).thenReturn(duplicate);

        DistributionEventResponse response = service.ingest(readPayload(), request("idem-001"));

        assertThat(response.getIdempotencyResult()).isEqualTo("DUPLICATE");
        assertThat(response.getCustomerId()).isEqualTo(101L);
        assertThat(response.getOrderId()).isEqualTo(202L);
        verify(customerMapper, never()).insert(any(Customer.class));
        verify(orderMapper, never()).insert(any(Order.class));

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(eventLogWriter).write(logCaptor.capture());
        assertThat(logCaptor.getValue().getIdempotencyStatus()).isEqualTo("DUPLICATE");
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldRefreshExistingExternalOrderWhenDuplicatePaidPayloadIsConsistent() throws Exception {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(eventLogMapper.selectOne(any())).thenReturn(null);
        Order existingOrder = existingDistributionOrder();
        existingOrder.setExternalTradeNo(null);
        existingOrder.setRawData("old-raw");
        when(orderMapper.selectOne(any())).thenReturn(existingOrder);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        DistributionEventResponse response = service.ingest(readPayload(), request("idem-existing-001"));

        assertThat(response.getIdempotencyResult()).isEqualTo("EXISTING");
        assertThat(response.getCustomerId()).isEqualTo(101L);
        assertThat(response.getOrderId()).isEqualTo(202L);
        verify(customerMapper, never()).insert(any(Customer.class));
        verify(orderMapper, never()).insert(any(Order.class));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getExternalTradeNo()).isEqualTo("pay_30001");
        assertThat(orderCaptor.getValue().getRawData()).contains("\"externalOrderId\":\"o_20001\"");
    }

    @Test
    void shouldQueueExceptionWhenDuplicatePaidOrderConflictsWithExistingSnapshot() throws Exception {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(eventLogMapper.selectOne(any())).thenReturn(null);
        Order existingOrder = existingDistributionOrder();
        existingOrder.setRawData("old-raw");
        when(orderMapper.selectOne(any())).thenReturn(existingOrder);

        DistributionEventResponse response = service.ingest(readConflictingPaidPayload(), request("idem-conflict-001"));

        assertThat(response.getIdempotencyResult()).isEqualTo("EXCEPTION_QUEUED");
        assertThat(response.getProcessStatus()).isEqualTo("SUCCESS");
        assertThat(response.getCustomerId()).isEqualTo(101L);
        assertThat(response.getOrderId()).isEqualTo(202L);
        assertThat(existingOrder.getRawData()).isEqualTo("old-raw");
        verify(customerMapper, never()).insert(any(Customer.class));
        verify(orderMapper, never()).insert(any(Order.class));
        verify(orderMapper, never()).updateById(any(Order.class));
        verify(distributionExceptionService).recordFailure(
                org.mockito.ArgumentMatchers.eq("DISTRIBUTION"),
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.eq("idem-conflict-001"),
                org.mockito.ArgumentMatchers.eq("EXTERNAL_ORDER_CONFLICT"),
                org.mockito.ArgumentMatchers.contains("amount"),
                org.mockito.ArgumentMatchers.eq(202L),
                org.mockito.ArgumentMatchers.eq("ORD-DIST-202"),
                org.mockito.ArgumentMatchers.contains("\"field\":\"amount\""));

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(eventLogWriter).write(logCaptor.capture());
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("SUCCESS");
        assertThat(logCaptor.getValue().getIdempotencyStatus()).isEqualTo("EXCEPTION_QUEUED");
        assertThat(logCaptor.getValue().getRelatedOrderId()).isEqualTo(202L);
    }

    @Test
    void shouldReevaluatePreviouslyQueuedExceptionEventInsteadOfTreatingItAsDuplicate() throws Exception {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        IntegrationCallbackEventLog queued = new IntegrationCallbackEventLog();
        queued.setProcessStatus("SUCCESS");
        queued.setIdempotencyStatus("EXCEPTION_QUEUED");
        queued.setRelatedCustomerId(101L);
        queued.setRelatedOrderId(202L);
        when(eventLogMapper.selectOne(any())).thenReturn(queued);
        when(orderMapper.selectOne(any())).thenReturn(existingDistributionOrder());

        DistributionEventResponse response = service.ingest(readConflictingPaidPayload(), request("idem-conflict-001"));

        assertThat(response.getIdempotencyResult()).isEqualTo("EXCEPTION_QUEUED");
        verify(orderMapper).selectOne(any());
        verify(distributionExceptionService).recordFailure(
                org.mockito.ArgumentMatchers.eq("DISTRIBUTION"),
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.eq("idem-conflict-001"),
                org.mockito.ArgumentMatchers.eq("EXTERNAL_ORDER_CONFLICT"),
                org.mockito.ArgumentMatchers.contains("duplicate external order conflict"),
                org.mockito.ArgumentMatchers.eq(202L),
                org.mockito.ArgumentMatchers.eq("ORD-DIST-202"),
                org.mockito.ArgumentMatchers.contains("\"field\":\"amount\""));
    }

    @Test
    void shouldRejectOrderStatusEventWithoutExistingExternalOrderAndKeepFailureLog() throws Exception {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(eventLogMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.ingest(readRefundPendingPayload(), request("idem-refund-001")))
                .isInstanceOf(com.seedcrm.crm.common.exception.BusinessException.class)
                .hasMessageContaining("external order does not exist");
        verify(customerMapper, never()).insert(any(Customer.class));
        verify(orderMapper, never()).insert(any(Order.class));

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(eventLogWriter).write(logCaptor.capture());
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("FAILED");
        assertThat(logCaptor.getValue().getIdempotencyStatus()).isEqualTo("FAILED");
        assertThat(logCaptor.getValue().getErrorCode()).isEqualTo("INGEST_FAILED");
        verify(distributionExceptionService).recordFailure(
                org.mockito.ArgumentMatchers.eq("DISTRIBUTION"),
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.eq("idem-refund-001"),
                org.mockito.ArgumentMatchers.eq("INGEST_FAILED"),
                org.mockito.ArgumentMatchers.contains("external order does not exist"));
    }

    @Test
    void shouldQueueManualHandlingWhenExternalRefundConflictsWithCompletedOrder() throws Exception {
        when(providerConfigMapper.selectOne(any())).thenReturn(distributionProvider());
        when(eventLogMapper.selectOne(any())).thenReturn(null);
        Order existingOrder = new Order();
        existingOrder.setId(202L);
        existingOrder.setCustomerId(101L);
        existingOrder.setStatus("COMPLETED");
        existingOrder.setExternalPartnerCode("DISTRIBUTION");
        existingOrder.setExternalOrderId("o_20001");
        when(orderMapper.selectOne(any())).thenReturn(existingOrder);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        DistributionEventResponse response = service.ingest(readRefundedPayload(), request("idem-refunded-completed-001"));

        assertThat(response.getIdempotencyResult()).isEqualTo("EXCEPTION_QUEUED");
        assertThat(response.getProcessStatus()).isEqualTo("SUCCESS");
        assertThat(response.getOrderId()).isEqualTo(202L);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(orderCaptor.getValue().getRefundStatus()).isEqualTo("refunded");
        assertThat(orderCaptor.getValue().getExternalStatus()).isEqualTo("refunded");

        verify(distributionExceptionService).recordFailure(
                org.mockito.ArgumentMatchers.eq("DISTRIBUTION"),
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.eq("idem-refunded-completed-001"),
                org.mockito.ArgumentMatchers.eq("EXTERNAL_STATUS_CONFLICT"),
                org.mockito.ArgumentMatchers.contains("COMPLETED"),
                org.mockito.ArgumentMatchers.eq(202L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.contains("\"field\":\"status\""));

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(eventLogWriter).write(logCaptor.capture());
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("SUCCESS");
        assertThat(logCaptor.getValue().getIdempotencyStatus()).isEqualTo("EXCEPTION_QUEUED");
        assertThat(logCaptor.getValue().getRelatedOrderId()).isEqualTo(202L);
    }

    @Test
    void shouldRejectUnknownPartnerAndKeepFailureLog() throws Exception {
        when(providerConfigMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.ingest(readPayload(), request("idem-unknown-partner")))
                .isInstanceOf(com.seedcrm.crm.common.exception.BusinessException.class)
                .hasMessageContaining("provider is not configured");
        verify(customerMapper, never()).insert(any(Customer.class));
        verify(orderMapper, never()).insert(any(Order.class));

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(eventLogWriter).write(logCaptor.capture());
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("FAILED");
        assertThat(logCaptor.getValue().getErrorCode()).isEqualTo("PROVIDER_INVALID");
        assertThat(logCaptor.getValue().getSignatureStatus()).isEqualTo("UNKNOWN");
        verify(distributionExceptionService).recordFailure(
                org.mockito.ArgumentMatchers.eq("DISTRIBUTION"),
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.eq("idem-unknown-partner"),
                org.mockito.ArgumentMatchers.eq("PROVIDER_INVALID"),
                org.mockito.ArgumentMatchers.contains("provider is not configured"));
    }

    @Test
    void shouldRejectPartnerMismatchBetweenHeaderAndPayload() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Partner-Code", "OTHER_PARTNER");
        request.addHeader("X-Idempotency-Key", "idem-mismatch");

        assertThatThrownBy(() -> service.ingest(readPayload(), request))
                .isInstanceOf(com.seedcrm.crm.common.exception.BusinessException.class)
                .hasMessageContaining("partner code mismatch");
        verify(customerMapper, never()).insert(any(Customer.class));
        verify(orderMapper, never()).insert(any(Order.class));

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(eventLogWriter).write(logCaptor.capture());
        assertThat(logCaptor.getValue().getErrorCode()).isEqualTo("PARTNER_MISMATCH");
    }

    @Test
    void shouldRejectLiveNonceReplayAfterSignatureVerified() throws Exception {
        String idempotencyKey = "idem-live-replay-001";
        String nonce = "nonce-replayed-001";
        String secret = "secret-001";
        JsonNode payload = readPayload();
        when(providerConfigMapper.selectOne(any())).thenReturn(liveDistributionProvider(secret));
        IntegrationCallbackEventLog replayed = new IntegrationCallbackEventLog();
        replayed.setSignatureStatus("VERIFIED");
        when(eventLogMapper.selectOne(any())).thenReturn(null, replayed);

        assertThatThrownBy(() -> service.ingest(payload, liveRequest(idempotencyKey, nonce, payload, secret)))
                .isInstanceOf(com.seedcrm.crm.common.exception.BusinessException.class)
                .hasMessageContaining("nonce replayed");
        verify(customerMapper, never()).insert(any(Customer.class));
        verify(orderMapper, never()).insert(any(Order.class));

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(eventLogWriter).write(logCaptor.capture());
        assertThat(logCaptor.getValue().getErrorCode()).isEqualTo("NONCE_REPLAYED");
        assertThat(logCaptor.getValue().getSignatureStatus()).isEqualTo("REPLAYED");
    }

    @Test
    void shouldReplayFromSchedulerWithoutExternalLiveSignature() throws Exception {
        when(providerConfigMapper.selectOne(any())).thenReturn(liveDistributionProvider("secret-001"));
        when(eventLogMapper.selectOne(any())).thenReturn(null);
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(customerMapper.insert(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(303L);
            return 1;
        });
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(404L);
            return 1;
        });

        DistributionEventResponse response = service.replayFromScheduler(readPayload(), "DISTRIBUTION", "idem-replay-001");

        assertThat(response.getIdempotencyResult()).isEqualTo("CREATED");
        assertThat(response.getCustomerId()).isEqualTo(303L);
        assertThat(response.getOrderId()).isEqualTo(404L);

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(eventLogWriter).write(logCaptor.capture());
        assertThat(logCaptor.getValue().getSignatureStatus()).isEqualTo("TRUSTED_REPLAY");
        assertThat(logCaptor.getValue().getTrustLevel()).isEqualTo("INTERNAL_REPLAY");
        assertThat(logCaptor.getValue().getCallbackPath()).isEqualTo("/scheduler/distribution/exceptions/replay");
    }

    private JsonNode readPayload() throws Exception {
        return objectMapper.readTree("""
                {
                  "eventType": "distribution.order.paid",
                  "eventId": "evt_001",
                  "partnerCode": "DISTRIBUTION",
                  "occurredAt": "2026-04-29T10:00:00+08:00",
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
    }

    private JsonNode readConflictingPaidPayload() throws Exception {
        return objectMapper.readTree("""
                {
                  "eventType": "distribution.order.paid",
                  "eventId": "evt_conflict_001",
                  "partnerCode": "DISTRIBUTION",
                  "occurredAt": "2026-04-29T10:00:00+08:00",
                  "member": {
                    "externalMemberId": "m_conflict_001",
                    "name": "Li Si",
                    "phone": "13900000000",
                    "role": "member"
                  },
                  "promoter": {
                    "externalPromoterId": "p_90002",
                    "role": "leader"
                  },
                  "order": {
                    "externalOrderId": "o_20001",
                    "externalTradeNo": "pay_conflict_001",
                    "type": "deposit",
                    "amount": 29900,
                    "paidAt": "2026-04-29T09:58:00+08:00",
                    "storeCode": "store_001",
                    "status": "paid"
                  },
                  "rawData": {}
                }
                """);
    }

    private JsonNode readRefundPendingPayload() throws Exception {
        return objectMapper.readTree("""
                {
                  "eventType": "distribution.order.refund_pending",
                  "eventId": "evt_refund_001",
                  "partnerCode": "DISTRIBUTION",
                  "occurredAt": "2026-04-29T10:00:00+08:00",
                  "order": {
                    "externalOrderId": "o_missing_001",
                    "externalTradeNo": "pay_missing_001",
                    "refundStatus": "refund_pending",
                    "status": "refund_pending"
                  },
                  "rawData": {}
                }
                """);
    }

    private JsonNode readRefundedPayload() throws Exception {
        return objectMapper.readTree("""
                {
                  "eventType": "distribution.order.refunded",
                  "eventId": "evt_refunded_001",
                  "partnerCode": "DISTRIBUTION",
                  "occurredAt": "2026-04-29T10:00:00+08:00",
                  "order": {
                    "externalOrderId": "o_20001",
                    "externalTradeNo": "pay_30001",
                    "refundStatus": "refunded",
                    "status": "refunded",
                    "refundAmount": 19900,
                    "refundAt": "2026-04-29T10:00:00+08:00"
                  },
                  "rawData": {}
                }
                """);
    }

    private IntegrationProviderConfig distributionProvider() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DISTRIBUTION");
        provider.setProviderName("External distribution");
        provider.setExecutionMode("MOCK");
        provider.setEnabled(1);
        return provider;
    }

    private Order existingDistributionOrder() {
        Order order = new Order();
        order.setId(202L);
        order.setOrderNo("ORD-DIST-202");
        order.setCustomerId(101L);
        order.setExternalPartnerCode("DISTRIBUTION");
        order.setExternalOrderId("o_20001");
        order.setExternalTradeNo("pay_30001");
        order.setExternalMemberId("m_10001");
        order.setExternalPromoterId("p_90001");
        order.setType(2);
        order.setAmount(new BigDecimal("199.00"));
        order.setStatus("PAID_DEPOSIT");
        order.setExternalStatus("paid");
        return order;
    }

    private IntegrationProviderConfig liveDistributionProvider(String secret) {
        IntegrationProviderConfig provider = distributionProvider();
        provider.setExecutionMode("LIVE");
        provider.setClientSecret(secret);
        return provider;
    }

    private MockHttpServletRequest request(String idempotencyKey) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Partner-Code", "DISTRIBUTION");
        request.addHeader("X-Idempotency-Key", idempotencyKey);
        return request;
    }

    private MockHttpServletRequest liveRequest(String idempotencyKey,
                                               String nonce,
                                               JsonNode payload,
                                               String secret) {
        MockHttpServletRequest request = request(idempotencyKey);
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).toString();
        request.addHeader("X-Timestamp", timestamp);
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Signature", hmacSha256(secret,
                timestamp + "|" + nonce + "|" + idempotencyKey + "|" + payload.toString()));
        return request;
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

    private static class MockHttpServletRequest extends org.springframework.mock.web.MockHttpServletRequest {
    }
}
