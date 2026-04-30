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

        assertThatThrownBy(() -> controller.listLeadsByGet(
                Map.of("app_id", "wrong-app", "timestamp", "2026-04-30T10:00:00Z", "sign", "bad"),
                new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("app_id 不匹配");

        String sign = hmacSha256("app-001|2026-04-30T10:00:00Z|1|30", "secret-001");
        ApiResponse<Map<String, Object>> response = controller.listLeadsByGet(
                Map.of("app_id", "app-001", "timestamp", "2026-04-30T10:00:00Z", "page", "1", "page_size", "30", "sign", sign),
                new MockHttpServletRequest());
        assertThat(response.getData().get("records")).isEqualTo(List.of());
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
