package com.seedcrm.crm.wecom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.wecom.entity.CustomerWecomRelation;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.entity.WecomTouchRule;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchLogMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchRuleMapper;
import com.seedcrm.crm.wecom.service.WecomTouchService;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class WecomTouchServiceImpl implements WecomTouchService {

    private static final String TRIGGER_TYPE_AUTO = "AUTO";
    private static final String TRIGGER_TYPE_MANUAL = "MANUAL";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";

    private final CustomerMapper customerMapper;
    private final CustomerWecomRelationMapper customerWecomRelationMapper;
    private final WecomTouchRuleMapper wecomTouchRuleMapper;
    private final WecomTouchLogMapper wecomTouchLogMapper;

    public WecomTouchServiceImpl(CustomerMapper customerMapper,
                                 CustomerWecomRelationMapper customerWecomRelationMapper,
                                 WecomTouchRuleMapper wecomTouchRuleMapper,
                                 WecomTouchLogMapper wecomTouchLogMapper) {
        this.customerMapper = customerMapper;
        this.customerWecomRelationMapper = customerWecomRelationMapper;
        this.wecomTouchRuleMapper = wecomTouchRuleMapper;
        this.wecomTouchLogMapper = wecomTouchLogMapper;
    }

    @Override
    @Transactional
    public void autoTrigger(Long customerId) {
        Customer customer = getCustomer(customerId);
        if (!StringUtils.hasText(customer.getTag())) {
            log.info("skip wecom auto trigger because customer tag is empty, customerId={}", customerId);
            return;
        }

        WecomTouchRule rule = getRule(customer.getTag(), TRIGGER_TYPE_AUTO);
        if (rule == null || !StringUtils.hasText(rule.getMessageTemplate())) {
            log.info("skip wecom auto trigger because no AUTO rule matched, customerId={}, tag={}",
                    customerId, customer.getTag());
            return;
        }

        sendAndLog(customer, rule.getMessageTemplate(), false);
    }

    @Override
    @Transactional
    public WecomTouchLog manualSend(Long customerId, String message) {
        Customer customer = getCustomer(customerId);
        String finalMessage = StringUtils.hasText(message) ? message : resolveManualMessage(customer);
        if (!StringUtils.hasText(finalMessage)) {
            throw new BusinessException("message is required");
        }
        return sendAndLog(customer, finalMessage, true);
    }

    private Customer getCustomer(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new BusinessException("customerId is required");
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("customer not found");
        }
        return customer;
    }

    private String resolveManualMessage(Customer customer) {
        if (customer == null || !StringUtils.hasText(customer.getTag())) {
            return null;
        }

        WecomTouchRule manualRule = getRule(customer.getTag(), TRIGGER_TYPE_MANUAL);
        if (manualRule != null && StringUtils.hasText(manualRule.getMessageTemplate())) {
            return manualRule.getMessageTemplate();
        }

        WecomTouchRule autoRule = getRule(customer.getTag(), TRIGGER_TYPE_AUTO);
        return autoRule == null ? null : autoRule.getMessageTemplate();
    }

    private WecomTouchRule getRule(String tag, String triggerType) {
        if (!StringUtils.hasText(tag) || !StringUtils.hasText(triggerType)) {
            return null;
        }
        return wecomTouchRuleMapper.selectOne(Wrappers.<WecomTouchRule>lambdaQuery()
                .eq(WecomTouchRule::getTag, tag)
                .eq(WecomTouchRule::getTriggerType, triggerType)
                .orderByAsc(WecomTouchRule::getId)
                .last("LIMIT 1"));
    }

    private WecomTouchLog sendAndLog(Customer customer, String message, boolean throwOnFailure) {
        CustomerWecomRelation relation = customerWecomRelationMapper.selectOne(Wrappers
                .<CustomerWecomRelation>lambdaQuery()
                .eq(CustomerWecomRelation::getCustomerId, customer.getId())
                .orderByAsc(CustomerWecomRelation::getId)
                .last("LIMIT 1"));

        if (relation == null || !StringUtils.hasText(relation.getExternalUserid())) {
            WecomTouchLog failedLog = saveLog(customer.getId(), null, message, STATUS_FAIL);
            log.warn("skip wecom touch because relation is missing, customerId={}", customer.getId());
            if (throwOnFailure) {
                throw new BusinessException("customer wecom relation not found");
            }
            return failedLog;
        }

        try {
            simulateSend(customer, relation, message);
            return saveLog(customer.getId(), relation.getExternalUserid(), message, STATUS_SUCCESS);
        } catch (Exception exception) {
            log.error("failed to send wecom touch, customerId={}, externalUserid={}",
                    customer.getId(), relation.getExternalUserid(), exception);
            WecomTouchLog failedLog = saveLog(customer.getId(), relation.getExternalUserid(), message, STATUS_FAIL);
            if (throwOnFailure) {
                throw new BusinessException("failed to send wecom message");
            }
            return failedLog;
        }
    }

    private void simulateSend(Customer customer, CustomerWecomRelation relation, String message) {
        log.info("simulate wecom send, customerId={}, externalUserid={}, wecomUserId={}, message={}",
                customer.getId(), relation.getExternalUserid(), relation.getWecomUserId(), message);
    }

    private WecomTouchLog saveLog(Long customerId, String externalUserid, String message, String status) {
        WecomTouchLog logEntity = new WecomTouchLog();
        logEntity.setCustomerId(customerId);
        logEntity.setExternalUserid(externalUserid);
        logEntity.setMessage(message);
        logEntity.setStatus(status);
        logEntity.setCreateTime(LocalDateTime.now());
        wecomTouchLogMapper.insert(logEntity);
        return logEntity;
    }
}
