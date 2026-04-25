package com.seedcrm.crm.wecom.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.wecom.entity.CustomerWecomRelation;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.entity.WecomTouchRule;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.wecom.mapper.WecomLiveCodeConfigMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchLogMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchRuleMapper;
import com.seedcrm.crm.wecom.service.WecomConsoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WecomTouchServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private CustomerWecomRelationMapper customerWecomRelationMapper;

    @Mock
    private WecomTouchRuleMapper wecomTouchRuleMapper;

    @Mock
    private WecomTouchLogMapper wecomTouchLogMapper;

    @Mock
    private WecomConsoleService wecomConsoleService;

    @Mock
    private WecomLiveCodeConfigMapper wecomLiveCodeConfigMapper;

    private WecomTouchServiceImpl wecomTouchService;

    @BeforeEach
    void setUp() {
        wecomTouchService = new WecomTouchServiceImpl(
                customerMapper,
                customerWecomRelationMapper,
                wecomTouchRuleMapper,
                wecomTouchLogMapper,
                wecomConsoleService,
                wecomLiveCodeConfigMapper,
                new ObjectMapper());
    }

    @Test
    void autoTriggerShouldSendAndWriteSuccessLog() {
        Customer customer = customer(1L, "SLEEP");
        CustomerWecomRelation relation = relation(1L, "ext-001", "seed-emp-01");
        WecomTouchRule rule = rule("SLEEP", "AUTO", "很久没来了，送你优惠券");

        when(customerMapper.selectById(1L)).thenReturn(customer);
        when(wecomTouchRuleMapper.selectOne(any())).thenReturn(rule);
        when(customerWecomRelationMapper.selectOne(any())).thenReturn(relation);
        when(wecomTouchLogMapper.insert(any(WecomTouchLog.class))).thenAnswer(invocation -> {
            WecomTouchLog log = invocation.getArgument(0);
            log.setId(10L);
            return 1;
        });

        wecomTouchService.autoTrigger(1L);

        verify(wecomTouchLogMapper).insert(any(WecomTouchLog.class));
    }

    @Test
    void autoTriggerShouldWriteFailLogWhenRelationMissing() {
        Customer customer = customer(2L, "SLEEP");
        WecomTouchRule rule = rule("SLEEP", "AUTO", "很久没来了，送你优惠券");

        when(customerMapper.selectById(2L)).thenReturn(customer);
        when(wecomTouchRuleMapper.selectOne(any())).thenReturn(rule);
        when(customerWecomRelationMapper.selectOne(any())).thenReturn(null);

        wecomTouchService.autoTrigger(2L);

        verify(wecomTouchLogMapper).insert(any(WecomTouchLog.class));
    }

    @Test
    void manualSendShouldUseProvidedMessage() {
        Customer customer = customer(3L, "HIGH_VALUE");
        CustomerWecomRelation relation = relation(3L, "ext-003", "seed-emp-03");

        when(customerMapper.selectById(3L)).thenReturn(customer);
        when(customerWecomRelationMapper.selectOne(any())).thenReturn(relation);
        when(wecomTouchLogMapper.insert(any(WecomTouchLog.class))).thenAnswer(invocation -> {
            WecomTouchLog log = invocation.getArgument(0);
            log.setId(30L);
            return 1;
        });

        WecomTouchLog log = wecomTouchService.manualSend(3L, "专属福利已到账");

        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getExternalUserid()).isEqualTo("ext-003");
        assertThat(log.getMessage()).isEqualTo("专属福利已到账");
    }

    @Test
    void manualSendShouldFallbackToManualRuleWhenMessageMissing() {
        Customer customer = customer(4L, "SLEEP");
        CustomerWecomRelation relation = relation(4L, "ext-004", "seed-emp-04");
        WecomTouchRule rule = rule("SLEEP", "MANUAL", "回来看看，我们给你准备了福利");

        when(customerMapper.selectById(4L)).thenReturn(customer);
        when(wecomTouchRuleMapper.selectOne(any())).thenReturn(rule);
        when(customerWecomRelationMapper.selectOne(any())).thenReturn(relation);
        when(wecomTouchLogMapper.insert(any(WecomTouchLog.class))).thenAnswer(invocation -> {
            WecomTouchLog log = invocation.getArgument(0);
            log.setId(40L);
            return 1;
        });

        WecomTouchLog log = wecomTouchService.manualSend(4L, null);

        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getMessage()).isEqualTo("回来看看，我们给你准备了福利");
    }

    @Test
    void manualSendShouldFailWhenRelationMissing() {
        Customer customer = customer(5L, "SLEEP");

        when(customerMapper.selectById(5L)).thenReturn(customer);
        when(wecomTouchRuleMapper.selectOne(any())).thenReturn(rule("SLEEP", "MANUAL", "测试消息"));
        when(customerWecomRelationMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> wecomTouchService.manualSend(5L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("企业微信关系");
        verify(wecomTouchLogMapper).insert(any(WecomTouchLog.class));
    }

    @Test
    void generateLiveCodeShouldReturnMockResultWhenLiveModeNotConfigured() {
        var response = wecomTouchService.generateLiveCode(
                "门店引流活码",
                "活动投放",
                "ROUND_ROBIN",
                java.util.List.of("私域客服A", "私域客服B"),
                java.util.List.of("private_domain_a", "private_domain_b"));

        assertThat(response.getCodeName()).isEqualTo("门店引流活码");
        assertThat(response.getContactWayId()).startsWith("cw_");
        assertThat(response.getQrCodeUrl()).startsWith("data:image/svg+xml");
    }

    private Customer customer(Long id, String tag) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setTag(tag);
        return customer;
    }

    private CustomerWecomRelation relation(Long customerId, String externalUserid, String wecomUserId) {
        CustomerWecomRelation relation = new CustomerWecomRelation();
        relation.setCustomerId(customerId);
        relation.setExternalUserid(externalUserid);
        relation.setWecomUserId(wecomUserId);
        return relation;
    }

    private WecomTouchRule rule(String tag, String triggerType, String messageTemplate) {
        WecomTouchRule rule = new WecomTouchRule();
        rule.setTag(tag);
        rule.setTriggerType(triggerType);
        rule.setMessageTemplate(messageTemplate);
        rule.setIsEnabled(1);
        return rule;
    }
}
