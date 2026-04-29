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

    private IntegrationProviderConfig distributionProvider() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DISTRIBUTION");
        provider.setProviderName("External distribution");
        provider.setExecutionMode("MOCK");
        provider.setEnabled(1);
        return provider;
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
