package com.seedcrm.crm.wecom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.wecom.dto.WecomLiveCodeGenerateResponse;
import com.seedcrm.crm.wecom.entity.CustomerWecomRelation;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.entity.WecomTouchRule;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchLogMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchRuleMapper;
import com.seedcrm.crm.wecom.service.WecomTouchService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
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
    private static final String STRATEGY_ROUND_ROBIN = "ROUND_ROBIN";
    private static final DateTimeFormatter LIVE_CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            throw new BusinessException("发送内容不能为空");
        }
        return sendAndLog(customer, finalMessage, true);
    }

    @Override
    public WecomLiveCodeGenerateResponse generateLiveCode(String codeName,
                                                          String scene,
                                                          String strategy,
                                                          List<String> employeeNames,
                                                          List<String> employeeAccounts) {
        if (!StringUtils.hasText(codeName)) {
            throw new BusinessException("活码名称不能为空");
        }
        if (employeeNames == null || employeeNames.isEmpty()) {
            throw new BusinessException("请至少选择一名轮询员工");
        }

        String finalScene = StringUtils.hasText(scene) ? scene.trim() : "营销引流";
        String finalStrategy = StringUtils.hasText(strategy) ? strategy.trim() : STRATEGY_ROUND_ROBIN;
        String contactWayId = "cw_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String generatedAt = LocalDateTime.now().format(LIVE_CODE_TIME_FORMATTER);
        String summary = String.format("活码已生成，当前按轮询策略分配给 %d 名私域客服", employeeNames.size());
        String qrCodeUrl = buildLiveCodeQrCode(codeName.trim(), employeeNames, contactWayId);
        String shortLink = "https://wecom.seedcrm.local/contact/" + contactWayId;

        log.info("simulate wecom live code generate, codeName={}, scene={}, strategy={}, employeeNames={}, employeeAccounts={}, contactWayId={}",
                codeName, finalScene, finalStrategy, employeeNames, employeeAccounts, contactWayId);

        return new WecomLiveCodeGenerateResponse(
                codeName.trim(),
                finalScene,
                finalStrategy,
                contactWayId,
                qrCodeUrl,
                shortLink,
                employeeNames.size(),
                employeeNames,
                generatedAt,
                summary);
    }

    private Customer getCustomer(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new BusinessException("客户 ID 不能为空");
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("未找到对应客户");
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
                throw new BusinessException("客户未绑定企业微信关系");
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
                throw new BusinessException("企业微信消息发送失败");
            }
            return failedLog;
        }
    }

    private void simulateSend(Customer customer, CustomerWecomRelation relation, String message) {
        log.info("simulate wecom send, customerId={}, externalUserid={}, wecomUserId={}, message={}",
                customer.getId(), relation.getExternalUserid(), relation.getWecomUserId(), message);
    }

    private String buildLiveCodeQrCode(String codeName, List<String> employeeNames, String contactWayId) {
        int size = 21;
        int cell = 10;
        int padding = 20;
        int width = size * cell + padding * 2;
        StringBuilder rects = new StringBuilder();
        String seed = contactWayId + "|" + codeName;

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (!shouldFillCell(seed, row, col, size)) {
                    continue;
                }
                rects.append("<rect x='")
                        .append(padding + col * cell)
                        .append("' y='")
                        .append(padding + row * cell)
                        .append("' width='8' height='8' rx='1.5' fill='#173042'/>");
            }
        }

        String employeeLine = String.join(" / ", employeeNames.stream().limit(3).toList());
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='" + width + "' height='" + (width + 78) + "' viewBox='0 0 "
                + width + " " + (width + 78) + "'>"
                + "<rect width='100%' height='100%' rx='24' fill='#fffdf9'/>"
                + "<rect x='10' y='10' width='" + (width - 20) + "' height='" + (width - 20) + "' rx='24' fill='#ffffff' stroke='#eadfce' stroke-width='2'/>"
                + rects
                + "<text x='50%' y='" + (width + 28) + "' text-anchor='middle' font-size='18' font-family='Microsoft YaHei, sans-serif' fill='#173042'>"
                + escapeSvg(codeName) + "</text>"
                + "<text x='50%' y='" + (width + 52) + "' text-anchor='middle' font-size='12' font-family='Microsoft YaHei, sans-serif' fill='#6d7d8c'>轮询员工："
                + escapeSvg(employeeLine) + "</text>"
                + "<text x='50%' y='" + (width + 70) + "' text-anchor='middle' font-size='10' font-family='Microsoft YaHei, sans-serif' fill='#9aa7b3'>"
                + escapeSvg(contactWayId) + "</text>"
                + "</svg>";

        return "data:image/svg+xml;charset=UTF-8," + URLEncoder.encode(svg, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean shouldFillCell(String seed, int row, int col, int size) {
        if (isFinderCell(row, col, size)) {
            return true;
        }
        if (isFinderBufferCell(row, col, size)) {
            return false;
        }
        int index = (row * size + col) % seed.length();
        int value = seed.charAt(index) + row * 11 + col * 17;
        return value % 3 == 0 || value % 5 == 0;
    }

    private boolean isFinderCell(int row, int col, int size) {
        return isFinderPattern(row, col, 0, 0)
                || isFinderPattern(row, col, 0, size - 7)
                || isFinderPattern(row, col, size - 7, 0);
    }

    private boolean isFinderBufferCell(int row, int col, int size) {
        return isFinderBuffer(row, col, 0, 0)
                || isFinderBuffer(row, col, 0, size - 7)
                || isFinderBuffer(row, col, size - 7, 0);
    }

    private boolean isFinderPattern(int row, int col, int top, int left) {
        if (row < top || row >= top + 7 || col < left || col >= left + 7) {
            return false;
        }
        int relativeRow = row - top;
        int relativeCol = col - left;
        return relativeRow == 0 || relativeRow == 6 || relativeCol == 0 || relativeCol == 6
                || (relativeRow >= 2 && relativeRow <= 4 && relativeCol >= 2 && relativeCol <= 4);
    }

    private boolean isFinderBuffer(int row, int col, int top, int left) {
        return row >= top - 1 && row <= top + 7 && col >= left - 1 && col <= left + 7 && !isFinderPattern(row, col, top, left);
    }

    private String escapeSvg(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
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
