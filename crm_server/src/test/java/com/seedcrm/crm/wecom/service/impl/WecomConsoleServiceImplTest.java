package com.seedcrm.crm.wecom.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.wecom.entity.WecomAppConfig;
import com.seedcrm.crm.wecom.mapper.WecomAppConfigMapper;
import com.seedcrm.crm.wecom.mapper.WecomLiveCodeConfigMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchLogMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchRuleMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WecomConsoleServiceImplTest {

    @Mock
    private WecomAppConfigMapper wecomAppConfigMapper;

    @Mock
    private WecomTouchRuleMapper wecomTouchRuleMapper;

    @Mock
    private WecomTouchLogMapper wecomTouchLogMapper;

    @Mock
    private WecomLiveCodeConfigMapper wecomLiveCodeConfigMapper;

    @Mock
    private IntegrationCallbackEventLogMapper integrationCallbackEventLogMapper;

    private WecomConsoleServiceImpl wecomConsoleService;

    @BeforeEach
    void setUp() {
        wecomConsoleService = new WecomConsoleServiceImpl(
                wecomAppConfigMapper,
                wecomTouchRuleMapper,
                wecomTouchLogMapper,
                wecomLiveCodeConfigMapper,
                integrationCallbackEventLogMapper,
                new ObjectMapper());
    }

    @Test
    void shouldReceiveWecomCallbackAndEchoChallengeWhenSkipVerifyEnabled() {
        WecomAppConfig config = new WecomAppConfig();
        config.setId(1L);
        config.setAppCode("PRIVATE_DOMAIN");
        config.setExecutionMode("LIVE");
        config.setSkipVerify(1);

        when(wecomAppConfigMapper.selectOne(any())).thenReturn(config);

        String response = wecomConsoleService.receiveCallback(
                "PRIVATE_DOMAIN",
                "/wecom/callback/PRIVATE_DOMAIN",
                "GET",
                Map.of("echostr", "challenge-token", "auth_code", "wecom-auth-001", "state", "seedcrm"),
                null);

        assertThat(response).isEqualTo("challenge-token");

        ArgumentCaptor<WecomAppConfig> configCaptor = ArgumentCaptor.forClass(WecomAppConfig.class);
        verify(wecomAppConfigMapper).updateById(configCaptor.capture());
        assertThat(configCaptor.getValue().getAuthStatus()).isEqualTo("AUTH_CODE_RECEIVED");
        assertThat(configCaptor.getValue().getAuthCode()).isEqualTo("wecom-auth-001");

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(integrationCallbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getProviderCode()).isEqualTo("WECOM");
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("SUCCESS");
    }
}
