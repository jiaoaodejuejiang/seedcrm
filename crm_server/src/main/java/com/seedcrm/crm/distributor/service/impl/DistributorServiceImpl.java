package com.seedcrm.crm.distributor.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.distributor.dto.DistributorCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorRuleCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorStatsResponse;
import com.seedcrm.crm.distributor.entity.Distributor;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorRule;
import com.seedcrm.crm.distributor.entity.DistributorSettlement;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.distributor.enums.DistributorRuleType;
import com.seedcrm.crm.distributor.mapper.DistributorMapper;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorRuleMapper;
import com.seedcrm.crm.distributor.mapper.DistributorSettlementMapper;
import com.seedcrm.crm.distributor.mapper.DistributorWithdrawMapper;
import com.seedcrm.crm.distributor.service.DistributorService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DistributorServiceImpl extends ServiceImpl<DistributorMapper, Distributor> implements DistributorService {

    private final DistributorMapper distributorMapper;
    private final ClueMapper clueMapper;
    private final OrderMapper orderMapper;
    private final DistributorRuleMapper distributorRuleMapper;
    private final DistributorIncomeDetailMapper distributorIncomeDetailMapper;
    private final DistributorSettlementMapper distributorSettlementMapper;
    private final DistributorWithdrawMapper distributorWithdrawMapper;

    public DistributorServiceImpl(DistributorMapper distributorMapper,
                                  ClueMapper clueMapper,
                                  OrderMapper orderMapper,
                                  DistributorRuleMapper distributorRuleMapper,
                                  DistributorIncomeDetailMapper distributorIncomeDetailMapper,
                                  DistributorSettlementMapper distributorSettlementMapper,
                                  DistributorWithdrawMapper distributorWithdrawMapper) {
        this.distributorMapper = distributorMapper;
        this.clueMapper = clueMapper;
        this.orderMapper = orderMapper;
        this.distributorRuleMapper = distributorRuleMapper;
        this.distributorIncomeDetailMapper = distributorIncomeDetailMapper;
        this.distributorSettlementMapper = distributorSettlementMapper;
        this.distributorWithdrawMapper = distributorWithdrawMapper;
    }

    @Override
    @Transactional
    public Distributor createDistributor(DistributorCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BusinessException("distributor name is required");
        }

        Distributor distributor = new Distributor();
        distributor.setName(request.getName().trim());
        distributor.setContactInfo(StringUtils.hasText(request.getContactInfo()) ? request.getContactInfo().trim() : null);
        distributor.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : "ACTIVE");
        distributor.setCreateTime(LocalDateTime.now());
        if (distributorMapper.insert(distributor) <= 0) {
            throw new BusinessException("failed to create distributor");
        }
        return distributor;
    }

    @Override
    @Transactional
    public DistributorRule saveRule(DistributorRuleCreateRequest request) {
        if (request == null) {
            throw new BusinessException("request body is required");
        }
        Distributor distributor = getByIdOrThrow(request.getDistributorId());
        DistributorRuleType ruleType = DistributorRuleType.fromCode(request.getRuleType());
        if (request.getRuleValue() == null || request.getRuleValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("ruleValue must be non-negative");
        }
        if (ruleType == DistributorRuleType.PERCENT && request.getRuleValue().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("percent ruleValue cannot exceed 1");
        }

        List<DistributorRule> activeRules = distributorRuleMapper.selectList(Wrappers.<DistributorRule>lambdaQuery()
                .eq(DistributorRule::getDistributorId, distributor.getId())
                .eq(DistributorRule::getIsActive, 1));
        for (DistributorRule activeRule : activeRules) {
            activeRule.setIsActive(0);
            distributorRuleMapper.updateById(activeRule);
        }

        DistributorRule rule = new DistributorRule();
        rule.setDistributorId(distributor.getId());
        rule.setRuleType(ruleType.name());
        rule.setRuleValue(request.getRuleValue());
        rule.setIsActive(request.getIsActive() == null ? 1 : request.getIsActive());
        rule.setCreateTime(LocalDateTime.now());
        if (distributorRuleMapper.insert(rule) <= 0) {
            throw new BusinessException("failed to save distributor rule");
        }
        return rule;
    }

    @Override
    public Distributor getByIdOrThrow(Long distributorId) {
        if (distributorId == null || distributorId <= 0) {
            throw new BusinessException("distributorId is required");
        }
        Distributor distributor = distributorMapper.selectById(distributorId);
        if (distributor == null) {
            throw new BusinessException("distributor not found");
        }
        return distributor;
    }

    @Override
    public DistributorStatsResponse getStats(Long distributorId) {
        getByIdOrThrow(distributorId);

        Long clueCount = clueMapper.selectCount(Wrappers.<Clue>lambdaQuery()
                .eq(Clue::getSourceChannel, SourceChannel.DISTRIBUTOR.name())
                .eq(Clue::getSourceId, distributorId));

        List<Order> distributorOrders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getSourceChannel, SourceChannel.DISTRIBUTOR.name())
                .eq(Order::getSourceId, distributorId));

        long orderCount = distributorOrders.size();
        long dealCustomerCount = distributorOrders.stream()
                .filter(order -> order.getCustomerId() != null)
                .filter(this::isDealOrder)
                .map(Order::getCustomerId)
                .distinct()
                .count();

        List<DistributorIncomeDetail> incomeDetails = distributorIncomeDetailMapper.selectList(
                Wrappers.<DistributorIncomeDetail>lambdaQuery()
                        .eq(DistributorIncomeDetail::getDistributorId, distributorId));
        BigDecimal totalIncome = incomeDetails.stream()
                .map(DistributorIncomeDetail::getIncomeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal settledIncome = incomeDetails.stream()
                .filter(detail -> detail.getSettlementId() != null)
                .map(DistributorIncomeDetail::getIncomeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unsettledIncome = incomeDetails.stream()
                .filter(detail -> detail.getSettlementId() == null)
                .map(DistributorIncomeDetail::getIncomeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal settledTotal = distributorSettlementMapper.selectList(
                        Wrappers.<DistributorSettlement>lambdaQuery()
                                .eq(DistributorSettlement::getDistributorId, distributorId))
                .stream()
                .map(DistributorSettlement::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal withdrawnTotal = distributorWithdrawMapper.selectList(
                        Wrappers.<DistributorWithdraw>lambdaQuery()
                                .eq(DistributorWithdraw::getDistributorId, distributorId))
                .stream()
                .map(DistributorWithdraw::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal withdrawableAmount = settledTotal.subtract(withdrawnTotal);
        if (withdrawableAmount.compareTo(BigDecimal.ZERO) < 0) {
            withdrawableAmount = BigDecimal.ZERO;
        }

        return new DistributorStatsResponse(
                distributorId,
                safeLong(clueCount),
                dealCustomerCount,
                orderCount,
                scaleMoney(totalIncome),
                scaleMoney(settledIncome),
                scaleMoney(unsettledIncome),
                scaleMoney(withdrawableAmount));
    }

    private boolean isDealOrder(Order order) {
        if (order == null || !StringUtils.hasText(order.getStatus())) {
            return false;
        }
        return OrderStatus.PAID_DEPOSIT.name().equals(order.getStatus())
                || OrderStatus.APPOINTMENT.name().equals(order.getStatus())
                || OrderStatus.ARRIVED.name().equals(order.getStatus())
                || OrderStatus.SERVING.name().equals(order.getStatus())
                || OrderStatus.COMPLETED.name().equals(order.getStatus());
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
