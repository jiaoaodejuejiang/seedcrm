package com.seedcrm.crm.wecom.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.wecom.entity.CustomerWecomRelation;
import com.seedcrm.crm.wecom.entity.WecomAppConfig;
import com.seedcrm.crm.wecom.mapper.WecomAppConfigMapper;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.wecom.mapper.WecomLiveCodeConfigMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchLogMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchRuleMapper;
import com.seedcrm.crm.wecom.support.WecomBindingStateCodec;
import com.seedcrm.crm.workbench.service.StaffDirectoryService;
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
    private CustomerWecomRelationMapper customerWecomRelationMapper;

    @Mock
    private IntegrationCallbackEventLogMapper integrationCallbackEventLogMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private StaffDirectoryService staffDirectoryService;

    private WecomConsoleServiceImpl wecomConsoleService;

    @BeforeEach
    void setUp() {
        wecomConsoleService = new WecomConsoleServiceImpl(
                wecomAppConfigMapper,
                wecomTouchRuleMapper,
                wecomTouchLogMapper,
                wecomLiveCodeConfigMapper,
                customerWecomRelationMapper,
                integrationCallbackEventLogMapper,
                orderMapper,
                staffDirectoryService,
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

    @Test
    void shouldBindCustomerWhenAddExternalContactCallbackCarriesValidState() {
        String state = WecomBindingStateCodec.encode(23L, 5101L, 15L, "13800005101");
        when(staffDirectoryService.getUserPhone(5101L)).thenReturn("13800005101");
        when(orderMapper.selectById(23L)).thenReturn(order(23L, 15L));
        when(customerWecomRelationMapper.selectOne(any())).thenReturn(null);

        String response = wecomConsoleService.receiveCallback(
                "PRIVATE_DOMAIN",
                "/wecom/callback/PRIVATE_DOMAIN",
                "POST",
                Map.of("state", state, "external_userid", "wm_001"),
                "{\"event\":\"add_external_contact\"}");

        assertThat(response).isEqualTo("success");
        ArgumentCaptor<CustomerWecomRelation> relationCaptor = ArgumentCaptor.forClass(CustomerWecomRelation.class);
        verify(customerWecomRelationMapper).insert(relationCaptor.capture());
        assertThat(relationCaptor.getValue().getCustomerId()).isEqualTo(15L);
        assertThat(relationCaptor.getValue().getExternalUserid()).isEqualTo("wm_001");
        assertThat(relationCaptor.getValue().getWecomUserId()).isEqualTo("5101");
    }

    @Test
    void shouldRejectBindingWhenEmployeePhoneHashDoesNotMatch() {
        String state = WecomBindingStateCodec.encode(23L, 5101L, 15L, "13800005101");
        when(staffDirectoryService.getUserPhone(5101L)).thenReturn("13900000000");

        wecomConsoleService.receiveCallback(
                "PRIVATE_DOMAIN",
                "/wecom/callback/PRIVATE_DOMAIN",
                "POST",
                Map.of("state", state, "external_userid", "wm_001"),
                "{\"event\":\"add_external_contact\"}");

        verify(customerWecomRelationMapper, never()).insert(any(CustomerWecomRelation.class));
        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(integrationCallbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("FAILED");
        assertThat(logCaptor.getValue().getProcessMessage()).contains("手机号校验失败");
    }

    @Test
    void shouldRejectBindingWhenStateCustomerDoesNotBelongToOrder() {
        String state = WecomBindingStateCodec.encode(23L, 5101L, 15L, "13800005101");
        when(staffDirectoryService.getUserPhone(5101L)).thenReturn("13800005101");
        when(orderMapper.selectById(23L)).thenReturn(order(23L, 99L));

        wecomConsoleService.receiveCallback(
                "PRIVATE_DOMAIN",
                "/wecom/callback/PRIVATE_DOMAIN",
                "POST",
                Map.of("state", state, "external_userid", "wm_001"),
                "{\"event\":\"add_external_contact\"}");

        verify(customerWecomRelationMapper, never()).insert(any(CustomerWecomRelation.class));
        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(integrationCallbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("FAILED");
        assertThat(logCaptor.getValue().getProcessMessage()).contains("客户与订单客户不一致");
    }

    @Test
    void shouldRejectBindingWhenCallbackUserDoesNotMatchStateUser() {
        String state = WecomBindingStateCodec.encode(23L, 5101L, 15L, "13800005101");
        when(staffDirectoryService.getUserPhone(5101L)).thenReturn("13800005101");
        when(staffDirectoryService.getWecomAccount(5101L)).thenReturn("store_service");
        when(orderMapper.selectById(23L)).thenReturn(order(23L, 15L));

        wecomConsoleService.receiveCallback(
                "PRIVATE_DOMAIN",
                "/wecom/callback/PRIVATE_DOMAIN",
                "POST",
                Map.of("state", state, "external_userid", "wm_001", "userid", "other_user"),
                "{\"event\":\"add_external_contact\"}");

        verify(customerWecomRelationMapper, never()).insert(any(CustomerWecomRelation.class));
        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(integrationCallbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("FAILED");
        assertThat(logCaptor.getValue().getProcessMessage()).contains("员工账号与 state 员工不一致");
    }

    private Order order(Long orderId, Long customerId) {
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        return order;
    }
}
