package com.seedcrm.crm.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.order.service.OrderVoucherVerificationResult;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultOrderVoucherVerificationGatewayTest {

    @Mock
    private IntegrationProviderConfigMapper providerConfigMapper;

    @Mock
    private SchedulerIntegrationService schedulerIntegrationService;

    private DefaultOrderVoucherVerificationGateway gateway;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        gateway = new DefaultOrderVoucherVerificationGateway(
                providerConfigMapper,
                schedulerIntegrationService,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void verifyShouldReturnMockSuccessForDouyinCoupon() {
        when(providerConfigMapper.selectOne(any())).thenReturn(provider("DOUYIN_LAIKE", "MOCK"));

        Order order = couponOrder(SourceChannel.DOUYIN.name());

        OrderVoucherVerificationResult result = gateway.verify(order, "DY-8801", "CODE");

        assertThat(result.providerCode()).isEqualTo("DOUYIN_LAIKE");
        assertThat(result.executionMode()).isEqualTo("MOCK");
        assertThat(result.externalVerified()).isTrue();
        assertThat(result.idempotencyKey()).contains("VOUCHER_VERIFY:DOUYIN_LAIKE:101:DY-8801");
    }

    @Test
    void verifyShouldReturnMockSuccessForDistributionProduct() {
        when(providerConfigMapper.selectOne(any())).thenReturn(provider("DISTRIBUTION", "MOCK"));

        Order order = couponOrder(SourceChannel.DISTRIBUTOR.name());
        order.setType(OrderType.DISTRIBUTION_PRODUCT.getCode());

        OrderVoucherVerificationResult result = gateway.verify(order, "FX-8802", "SCAN");

        assertThat(result.providerCode()).isEqualTo("DISTRIBUTION");
        assertThat(result.externalVerified()).isTrue();
    }

    @Test
    void verifyShouldRejectMissingProviderConfig() {
        when(providerConfigMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> gateway.verify(couponOrder(SourceChannel.DOUYIN.name()), "DY-8803", "CODE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DOUYIN_LAIKE");
    }

    @Test
    void verifyShouldAcceptLiveResponseWithExplicitSuccess() throws IOException {
        when(providerConfigMapper.selectOne(any())).thenReturn(liveProvider("{\"code\":0,\"message\":\"ok\"}"));

        OrderVoucherVerificationResult result = gateway.verify(couponOrder(SourceChannel.DOUYIN.name()), "DY-8805", "CODE");

        assertThat(result.executionMode()).isEqualTo("LIVE");
        assertThat(result.externalVerified()).isTrue();
        assertThat(result.responsePayload()).contains("\"code\":0");
    }

    @Test
    void verifyShouldAcceptAbsoluteVoucherVerifyUrl() throws IOException {
        IntegrationProviderConfig provider = liveProvider("{\"success\":true}");
        provider.setBaseUrl("http://unused.example.com");
        provider.setVoucherVerifyPath("http://127.0.0.1:" + server.getAddress().getPort() + "/voucher/verify");
        when(providerConfigMapper.selectOne(any())).thenReturn(provider);

        OrderVoucherVerificationResult result = gateway.verify(couponOrder(SourceChannel.DOUYIN.name()), "DY-8810", "CODE");

        assertThat(result.executionMode()).isEqualTo("LIVE");
        assertThat(result.externalVerified()).isTrue();
        assertThat(result.responsePayload()).contains("\"success\":true");
    }

    @Test
    void verifyShouldRejectLiveResponseWithFailureCode() throws IOException {
        when(providerConfigMapper.selectOne(any())).thenReturn(liveProvider("{\"code\":4001,\"message\":\"券码不存在\"}"));

        assertThatThrownBy(() -> gateway.verify(couponOrder(SourceChannel.DOUYIN.name()), "DY-8806", "CODE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("券码不存在");
    }

    @Test
    void verifyShouldRejectLiveEmptyResponse() throws IOException {
        when(providerConfigMapper.selectOne(any())).thenReturn(liveProvider(""));

        assertThatThrownBy(() -> gateway.verify(couponOrder(SourceChannel.DOUYIN.name()), "DY-8807", "CODE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("返回为空");
    }

    @Test
    void verifyShouldRejectLiveHtmlResponse() throws IOException {
        when(providerConfigMapper.selectOne(any())).thenReturn(liveProvider("<html>bad gateway</html>"));

        assertThatThrownBy(() -> gateway.verify(couponOrder(SourceChannel.DOUYIN.name()), "DY-8808", "CODE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("有效 JSON");
    }

    @Test
    void verifyShouldRejectLiveJsonWithoutExplicitSuccess() throws IOException {
        when(providerConfigMapper.selectOne(any())).thenReturn(liveProvider("{\"data\":{\"order_id\":\"x\"}}"));

        assertThatThrownBy(() -> gateway.verify(couponOrder(SourceChannel.DOUYIN.name()), "DY-8809", "CODE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("明确成功标记");
    }

    @Test
    void verifyShouldRejectUnknownCouponSource() {
        Order order = couponOrder("OTHER");

        assertThatThrownBy(() -> gateway.verify(order, "OT-8804", "CODE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源无法匹配核销通道");
        verify(providerConfigMapper, never()).selectOne(any());
    }

    @Test
    void verifyShouldSkipDepositOrder() {
        Order order = couponOrder(SourceChannel.DOUYIN.name());
        order.setType(OrderType.DEPOSIT.getCode());

        OrderVoucherVerificationResult result = gateway.verify(order, "DIRECT-DEPOSIT-101", "DIRECT_DEPOSIT");

        assertThat(result.externalVerified()).isFalse();
        assertThat(result.executionMode()).isEqualTo("LOCAL");
        verify(providerConfigMapper, never()).selectOne(any());
    }

    private Order couponOrder(String sourceChannel) {
        Order order = new Order();
        order.setId(101L);
        order.setOrderNo("ORD202604211234567101");
        order.setType(OrderType.COUPON.getCode());
        order.setSourceChannel(sourceChannel);
        return order;
    }

    private IntegrationProviderConfig provider(String providerCode, String executionMode) {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setProviderCode(providerCode);
        provider.setExecutionMode(executionMode);
        provider.setEnabled(1);
        return provider;
    }

    private IntegrationProviderConfig liveProvider(String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/voucher/verify", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        IntegrationProviderConfig provider = provider("DOUYIN_LAIKE", "LIVE");
        provider.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        provider.setVoucherVerifyPath("/voucher/verify");
        provider.setVerifyCodeField("encrypted_codes");
        provider.setRequestTimeoutMs(1000);
        return provider;
    }
}
