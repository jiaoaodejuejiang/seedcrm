package com.seedcrm.crm.scheduler.support;

import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

public final class SchedulerRestClientFactory {

    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static final int MIN_TIMEOUT_MS = 1_000;
    private static final int MAX_TIMEOUT_MS = 60_000;

    private SchedulerRestClientFactory() {
    }

    public static RestClient build(IntegrationProviderConfig provider) {
        int timeoutMs = resolveRequestTimeoutMs(provider);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(timeoutMs);
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public static int resolveRequestTimeoutMs(IntegrationProviderConfig provider) {
        Integer configured = provider == null ? null : provider.getRequestTimeoutMs();
        if (configured == null || configured <= 0) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.min(Math.max(configured, MIN_TIMEOUT_MS), MAX_TIMEOUT_MS);
    }

    public static String timeoutSummary(IntegrationProviderConfig provider) {
        String providerCode = provider == null ? null : provider.getProviderCode();
        String prefix = StringUtils.hasText(providerCode) ? providerCode.trim() : "UNKNOWN_PROVIDER";
        return prefix + " timeout=" + resolveRequestTimeoutMs(provider) + "ms";
    }
}
