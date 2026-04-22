package com.seedcrm.crm.distributor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorRule;
import com.seedcrm.crm.distributor.enums.DistributorRuleType;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorRuleMapper;
import com.seedcrm.crm.distributor.service.DistributorIncomeService;
import com.seedcrm.crm.distributor.service.DistributorService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DistributorIncomeServiceImpl implements DistributorIncomeService {

    private static final int ACTIVE_FLAG = 1;

    private final OrderMapper orderMapper;
    private final DistributorRuleMapper distributorRuleMapper;
    private final DistributorIncomeDetailMapper distributorIncomeDetailMapper;
    private final DistributorService distributorService;

    public DistributorIncomeServiceImpl(OrderMapper orderMapper,
                                        DistributorRuleMapper distributorRuleMapper,
                                        DistributorIncomeDetailMapper distributorIncomeDetailMapper,
                                        DistributorService distributorService) {
        this.orderMapper = orderMapper;
        this.distributorRuleMapper = distributorRuleMapper;
        this.distributorIncomeDetailMapper = distributorIncomeDetailMapper;
        this.distributorService = distributorService;
    }

    @Override
    @Transactional
    public DistributorIncomeDetail calculate(Long orderId) {
        validateOrderId(orderId);
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        if (!SourceChannel.DISTRIBUTOR.name().equals(order.getSourceChannel()) || order.getSourceId() == null) {
            return null;
        }
        if (!OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            throw new BusinessException("order must be completed before distributor income calculation");
        }
        if (order.getAmount() == null || order.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("order amount must be greater than 0");
        }

        DistributorIncomeDetail existingDetail = distributorIncomeDetailMapper.selectOne(
                new LambdaQueryWrapper<DistributorIncomeDetail>()
                        .eq(DistributorIncomeDetail::getOrderId, orderId)
                        .last("LIMIT 1"));
        if (existingDetail != null) {
            return existingDetail;
        }

        Long distributorId = order.getSourceId();
        distributorService.getByIdOrThrow(distributorId);
        DistributorRule rule = distributorRuleMapper.selectOne(new LambdaQueryWrapper<DistributorRule>()
                .eq(DistributorRule::getDistributorId, distributorId)
                .eq(DistributorRule::getIsActive, ACTIVE_FLAG)
                .orderByDesc(DistributorRule::getCreateTime, DistributorRule::getId)
                .last("LIMIT 1"));
        if (rule == null) {
            throw new BusinessException("active distributor rule not found");
        }

        DistributorIncomeDetail detail = new DistributorIncomeDetail();
        detail.setDistributorId(distributorId);
        detail.setOrderId(orderId);
        detail.setOrderAmount(scaleMoney(order.getAmount()));
        detail.setIncomeAmount(calculateIncome(order.getAmount(), rule));
        detail.setCreateTime(LocalDateTime.now());
        if (distributorIncomeDetailMapper.insert(detail) <= 0) {
            throw new BusinessException("failed to create distributor income detail");
        }
        return detail;
    }

    private BigDecimal calculateIncome(BigDecimal orderAmount, DistributorRule rule) {
        BigDecimal ruleValue = rule.getRuleValue();
        if (ruleValue == null || ruleValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("distributor rule value must be non-negative");
        }
        return switch (DistributorRuleType.fromCode(rule.getRuleType())) {
            case PERCENT -> scaleMoney(orderAmount.multiply(ruleValue));
            case FIXED -> scaleMoney(ruleValue);
        };
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException("orderId is required");
        }
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
