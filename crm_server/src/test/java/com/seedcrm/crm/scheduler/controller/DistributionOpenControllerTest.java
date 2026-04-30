package com.seedcrm.crm.scheduler.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.service.DistributionEventIngestService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class DistributionOpenControllerTest {

    @Mock
    private IntegrationProviderConfigMapper providerConfigMapper;

    @Mock
    private DistributionEventIngestService distributionEventIngestService;

    private DistributionOpenController controller;

    @BeforeEach
    void setUp() {
        controller = new DistributionOpenController(providerConfigMapper, distributionEventIngestService);
    }

    @Test
    void shouldKeepDeprecatedLeadsEndpointWithoutReturningClueData() {
        when(providerConfigMapper.selectOne(any())).thenReturn(mockProvider());

        ApiResponse<Map<String, Object>> response = controller.listLeadsByGet(
                Map.of("page", "1", "page_size", "30"),
                new MockHttpServletRequest());

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).containsEntry("deprecatedEndpoint", true);
        assertThat(response.getData()).containsEntry("supportedInboundEndpoint", "/open/distribution/events");
        assertThat(response.getData()).containsEntry("clueCreated", false);
        assertThat(response.getData()).containsEntry("customerOrderCreatedByThisEndpoint", false);
        assertThat(response.getData().get("records")).isEqualTo(List.of());
        assertThat(String.valueOf(response.getData().get("message"))).contains("分销方案B");
    }

    @Test
    void shouldStillValidateDeprecatedLeadsEndpointInLiveMode() {
        IntegrationProviderConfig provider = mockProvider();
        provider.setExecutionMode("LIVE");
        provider.setAppId("app-001");
        provider.setClientSecret("secret-001");
        when(providerConfigMapper.selectOne(any())).thenReturn(provider);

        String mismatchTimestamp = Instant.now().toString();
        assertThatThrownBy(() -> controller.listLeadsByGet(
                Map.of("app_id", "wrong-app", "timestamp", mismatchTimestamp, "nonce", "nonce-mismatch", "sign", "bad"),
                new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("app_id 不匹配");

        String timestamp = Instant.now().toString();
        String nonce = "nonce-live-compatible-001";
        String sign = hmacSha256("app-001|" + timestamp + "|" + nonce + "|1|30", "secret-001");
        ApiResponse<Map<String, Object>> response = controller.listLeadsByGet(
                Map.of("app_id", "app-001", "timestamp", timestamp, "nonce", nonce, "page", "1", "page_size", "30", "sign", sign),
                new MockHttpServletRequest());
        assertThat(response.getData().get("records")).isEqualTo(List.of());
        assertThat(response.getData().get("securityPolicy")).asString().contains("nonce");
    }

    @Test
    void shouldRejectDeprecatedLeadsEndpointWhenTimestampExpiredOrNonceReplayed() {
        IntegrationProviderConfig provider = mockProvider();
        provider.setExecutionMode("LIVE");
        provider.setAppId("app-002");
        provider.setClientSecret("secret-002");
        when(providerConfigMapper.selectOne(any())).thenReturn(provider);

        String expiredTimestamp = Instant.now().minusSeconds(3600).toString();
        String expiredSign = hmacSha256("app-002|" + expiredTimestamp + "|nonce-expired|1|30", "secret-002");
        assertThatThrownBy(() -> controller.listLeadsByGet(
                Map.of("app_id", "app-002", "timestamp", expiredTimestamp, "nonce", "nonce-expired", "page", "1", "page_size", "30", "sign", expiredSign),
                new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("timestamp");

        String timestamp = Instant.now().toString();
        String nonce = "nonce-replay-001";
        String sign = hmacSha256("app-002|" + timestamp + "|" + nonce + "|1|30", "secret-002");
        controller.listLeadsByGet(
                Map.of("app_id", "app-002", "timestamp", timestamp, "nonce", nonce, "page", "1", "page_size", "30", "sign", sign),
                new MockHttpServletRequest());

        assertThatThrownBy(() -> controller.listLeadsByGet(
                Map.of("app_id", "app-002", "timestamp", timestamp, "nonce", nonce, "page", "1", "page_size", "30", "sign", sign),
                new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nonce");
    }

    @Test
    void shouldApplyConfiguredRateLimitForDeprecatedLeadsEndpoint() {
        IntegrationProviderConfig provider = mockProvider();
        provider.setProviderCode("DISTRIBUTION_RATE_LIMIT");
        provider.setExecutionMode("LIVE");
        provider.setAppId("app-rate-limit");
        provider.setClientSecret("secret-rate-limit");
        provider.setRateLimitPerMinute(1);
        when(providerConfigMapper.selectOne(any())).thenReturn(provider);

        String firstTimestamp = Instant.now().toString();
        String firstNonce = "nonce-rate-limit-001";
        String firstSign = hmacSha256("app-rate-limit|" + firstTimestamp + "|" + firstNonce + "|1|30", "secret-rate-limit");
        controller.listLeadsByGet(
                Map.of("app_id", "app-rate-limit", "timestamp", firstTimestamp, "nonce", firstNonce, "page", "1", "page_size", "30", "sign", firstSign),
                new MockHttpServletRequest());

        String secondTimestamp = Instant.now().toString();
        String secondNonce = "nonce-rate-limit-002";
        String secondSign = hmacSha256("app-rate-limit|" + secondTimestamp + "|" + secondNonce + "|1|30", "secret-rate-limit");
        assertThatThrownBy(() -> controller.listLeadsByGet(
                Map.of("app_id", "app-rate-limit", "timestamp", secondTimestamp, "nonce", secondNonce, "page", "1", "page_size", "30", "sign", secondSign),
                new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求过于频繁");
    }

    @Test
    void shouldUseConfiguredCacheForDeprecatedLeadsEndpoint() {
        IntegrationProviderConfig provider = mockProvider();
        provider.setProviderCode("DISTRIBUTION_CACHE_TEST");
        provider.setCacheTtlSeconds(60);
        when(providerConfigMapper.selectOne(any())).thenReturn(provider);

        ApiResponse<Map<String, Object>> first = controller.listLeadsByGet(
                Map.of("page", "2", "page_size", "30"),
                new MockHttpServletRequest());
        ApiResponse<Map<String, Object>> second = controller.listLeadsByGet(
                Map.of("page", "2", "page_size", "30"),
                new MockHttpServletRequest());

        assertThat(first.getData()).containsEntry("cacheHit", false);
        assertThat(second.getData()).containsEntry("cacheHit", true);
        assertThat(second.getData().get("cacheExpiresAt")).isEqualTo(first.getData().get("cacheExpiresAt"));
        assertThat(second.getData().get("traceId")).isNotEqualTo(first.getData().get("traceId"));
        assertThat(second.getData().get("records")).isEqualTo(List.of());
    }

    private IntegrationProviderConfig mockProvider() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setProviderCode("DISTRIBUTION");
        provider.setExecutionMode("MOCK");
        provider.setEnabled(1);
        return provider;
    }

    private String hmacSha256(String source, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
