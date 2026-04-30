package com.seedcrm.crm.scheduler.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import org.junit.jupiter.api.Test;

class SchedulerRestClientFactoryTest {

    @Test
    void shouldResolveSafeRequestTimeoutFromProviderConfig() {
        assertThat(SchedulerRestClientFactory.resolveRequestTimeoutMs(null)).isEqualTo(10_000);

        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setProviderCode("DISTRIBUTION");

        provider.setRequestTimeoutMs(15_000);
        assertThat(SchedulerRestClientFactory.resolveRequestTimeoutMs(provider)).isEqualTo(15_000);

        provider.setRequestTimeoutMs(300);
        assertThat(SchedulerRestClientFactory.resolveRequestTimeoutMs(provider)).isEqualTo(1_000);

        provider.setRequestTimeoutMs(90_000);
        assertThat(SchedulerRestClientFactory.resolveRequestTimeoutMs(provider)).isEqualTo(60_000);

        provider.setRequestTimeoutMs(null);
        assertThat(SchedulerRestClientFactory.timeoutSummary(provider)).isEqualTo("DISTRIBUTION timeout=10000ms");
    }
}
