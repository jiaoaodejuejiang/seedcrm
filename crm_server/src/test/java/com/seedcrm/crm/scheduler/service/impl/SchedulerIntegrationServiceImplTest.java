package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackConfig;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackConfigMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulerIntegrationServiceImplTest {

    @Mock
    private IntegrationProviderConfigMapper providerConfigMapper;

    @Mock
    private IntegrationCallbackConfigMapper callbackConfigMapper;

    @Mock
    private IntegrationCallbackEventLogMapper callbackEventLogMapper;

    private SchedulerIntegrationServiceImpl schedulerIntegrationService;

    @BeforeEach
    void setUp() {
        schedulerIntegrationService = new SchedulerIntegrationServiceImpl(
                providerConfigMapper,
                callbackConfigMapper,
                callbackEventLogMapper,
                new ObjectMapper());
    }

    @Test
    void shouldReceiveDouyinCallbackAndUpdateStatus() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DOUYIN_LAIKE");
        provider.setProviderName("抖音来客线索");
        provider.setModuleCode("CLUE");
        provider.setExecutionMode("LIVE");
        provider.setCallbackUrl("http://127.0.0.1:8080/scheduler/oauth/douyin/callback");
        provider.setAccessToken("live-access-token");
        provider.setTokenExpiresAt(LocalDateTime.now().plusHours(1));

        IntegrationCallbackConfig callback = new IntegrationCallbackConfig();
        callback.setId(2L);
        callback.setProviderCode("DOUYIN_LAIKE");
        callback.setCallbackUrl("http://127.0.0.1:8080/scheduler/oauth/douyin/callback");
        callback.setCallbackName("抖音来客授权回调");

        when(providerConfigMapper.selectOne(any())).thenReturn(provider);
        when(callbackConfigMapper.selectOne(any())).thenReturn(callback);

        IntegrationProviderConfig result = schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客授权回调",
                "/scheduler/oauth/douyin/callback",
                "GET",
                Map.of("code", "auth-code-001", "state", "seedcrm-douyin"),
                null);

        assertThat(result).isNotNull();
        assertThat(result.getAuthStatus()).isEqualTo("AUTH_CODE_RECEIVED");
        assertThat(result.getAuthCodeMasked()).isNotBlank();

        ArgumentCaptor<IntegrationProviderConfig> providerCaptor = ArgumentCaptor.forClass(IntegrationProviderConfig.class);
        verify(providerConfigMapper).updateById(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getAuthCode()).isEqualTo("auth-code-001");
        assertThat(providerCaptor.getValue().getLastCallbackStatus()).isEqualTo("SUCCESS");

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(callbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getProviderCode()).isEqualTo("DOUYIN_LAIKE");
        assertThat(logCaptor.getValue().getAuthCode()).contains("****");
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("SUCCESS");
    }
}
