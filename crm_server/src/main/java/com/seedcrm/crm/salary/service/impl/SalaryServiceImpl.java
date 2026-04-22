package com.seedcrm.crm.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.finance.enums.LedgerBizType;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.OrderRoleRecordMapper;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.salary.dto.SalaryBalanceResponse;
import com.seedcrm.crm.salary.dto.SalaryStatResponse;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.SalaryRule;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.enums.SalaryRuleType;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.salary.mapper.SalaryRuleMapper;
import com.seedcrm.crm.salary.mapper.SalarySettlementMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import com.seedcrm.crm.salary.service.SalaryService;
import com.seedcrm.crm.risk.enums.IdempotentBizType;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.risk.service.IdempotentService;
import com.seedcrm.crm.risk.service.RiskControlService;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalaryServiceImpl implements SalaryService {

    private static final int ACTIVE_FLAG = 1;

    private final OrderRoleRecordMapper orderRoleRecordMapper;
    private final PlanOrderMapper planOrderMapper;
    private final OrderMapper orderMapper;
    private final SalaryRuleMapper salaryRuleMapper;
    private final SalaryDetailMapper salaryDetailMapper;
    private final SalarySettlementMapper salarySettlementMapper;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final FinanceService financeService;
    private final DbLockService dbLockService;
    private final IdempotentService idempotentService;
    private final RiskControlService riskControlService;

    public SalaryServiceImpl(OrderRoleRecordMapper orderRoleRecordMapper,
                             PlanOrderMapper planOrderMapper,
                             OrderMapper orderMapper,
                             SalaryRuleMapper salaryRuleMapper,
                             SalaryDetailMapper salaryDetailMapper,
                             SalarySettlementMapper salarySettlementMapper,
                             WithdrawRecordMapper withdrawRecordMapper,
                             FinanceService financeService,
                             DbLockService dbLockService,
                             IdempotentService idempotentService,
                             RiskControlService riskControlService) {
        this.orderRoleRecordMapper = orderRoleRecordMapper;
        this.planOrderMapper = planOrderMapper;
        this.orderMapper = orderMapper;
        this.salaryRuleMapper = salaryRuleMapper;
        this.salaryDetailMapper = salaryDetailMapper;
        this.salarySettlementMapper = salarySettlementMapper;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.financeService = financeService;
        this.dbLockService = dbLockService;
        this.idempotentService = idempotentService;
        this.riskControlService = riskControlService;
    }

    @Override
    public SalaryStatResponse stat(Long userId) {
        validateUserId(userId);
        Long orderCount = defaultCount(orderRoleRecordMapper.countDistinctPlanOrdersByUserId(userId));
        Long serviceCount = defaultCount(orderRoleRecordMapper.countFinishedServicesByUserId(userId));
        Map<String, Long> roleDistribution = buildRoleDistribution(
                orderRoleRecordMapper.selectRoleDistributionByUserId(userId));
        return new SalaryStatResponse(userId, orderCount, roleDistribution, serviceCount);
    }

    @Override
    public SalaryBalanceResponse balance(Long userId) {
        validateUserId(userId);
        BigDecimal unsettledAmount = sumSalaryDetails(salaryDetailMapper.selectList(new LambdaQueryWrapper<SalaryDetail>()
                .eq(SalaryDetail::getUserId, userId)
                .isNull(SalaryDetail::getSettlementId)));
        BigDecimal settledAmount = sumSettlements(salarySettlementMapper.selectList(new LambdaQueryWrapper<SalarySettlement>()
                .eq(SalarySettlement::getUserId, userId)));
        BigDecimal withdrawnAmount = sumWithdraws(withdrawRecordMapper.selectList(new LambdaQueryWrapper<WithdrawRecord>()
                .eq(WithdrawRecord::getUserId, userId)));
        BigDecimal withdrawableAmount = settledAmount.subtract(withdrawnAmount);
        if (withdrawableAmount.compareTo(BigDecimal.ZERO) < 0) {
            withdrawableAmount = BigDecimal.ZERO;
        }
        return new SalaryBalanceResponse(userId, scaleMoney(unsettledAmount), scaleMoney(settledAmount),
                scaleMoney(withdrawnAmount), scaleMoney(withdrawableAmount));
    }

    @Override
    @Transactional
    public List<SalaryDetail> calculateForPlanOrder(Long planOrderId) {
        return calculateInternal(planOrderId, true);
    }

    @Override
    @Transactional
    public List<SalaryDetail> recalculateForPlanOrder(Long planOrderId) {
        validatePlanOrderId(planOrderId);
        PlanOrder planOrder = getPlanOrder(planOrderId);
        ensurePlanOrderFinished(planOrder);

        List<SalaryDetail> existingDetails = salaryDetailMapper.selectList(new LambdaQueryWrapper<SalaryDetail>()
                .eq(SalaryDetail::getPlanOrderId, planOrderId)
                .orderByAsc(SalaryDetail::getCreateTime, SalaryDetail::getId));
        for (SalaryDetail detail : existingDetails) {
            if (detail.getSettlementId() != null) {
                throw new BusinessException("cannot recalculate salary after settlement");
            }
            if (detail.getId() != null && financeService.hasLedgerRecord(LedgerBizType.SALARY, detail.getId())) {
                throw new BusinessException("cannot recalculate salary after ledger posting");
            }
        }
        if (!existingDetails.isEmpty()) {
            salaryDetailMapper.delete(new LambdaQueryWrapper<SalaryDetail>()
                    .eq(SalaryDetail::getPlanOrderId, planOrderId));
        }
        return calculateInternal(planOrderId, false);
    }

    private List<SalaryDetail> calculateInternal(Long planOrderId, boolean reuseExisting) {
        validatePlanOrderId(planOrderId);
        PlanOrder planOrder = dbLockService.lockPlanOrder(planOrderId);
        ensurePlanOrderFinished(planOrder);
        String bizKey = "SALARY_" + planOrderId;

        List<SalaryDetail> existingDetails = salaryDetailMapper.selectList(new LambdaQueryWrapper<SalaryDetail>()
                .eq(SalaryDetail::getPlanOrderId, planOrderId)
                .orderByAsc(SalaryDetail::getCreateTime, SalaryDetail::getId));
        if (!idempotentService.tryStart(bizKey, IdempotentBizType.SALARY)) {
            return existingDetails;
        }
        if (reuseExisting && !existingDetails.isEmpty()) {
            idempotentService.markSuccess(bizKey);
            return existingDetails;
        }

        Order order = orderMapper.selectById(planOrder.getOrderId());
        if (order == null) {
            throw new BusinessException("linked order not found for salary calculation");
        }
        if (order.getAmount() == null || order.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("order amount must be greater than 0 for salary calculation");
        }

        List<OrderRoleRecord> roleRecords = orderRoleRecordMapper.selectList(new LambdaQueryWrapper<OrderRoleRecord>()
                .eq(OrderRoleRecord::getPlanOrderId, planOrderId)
                .eq(OrderRoleRecord::getIsCurrent, ACTIVE_FLAG)
                .orderByAsc(OrderRoleRecord::getCreateTime, OrderRoleRecord::getId));
        if (roleRecords.isEmpty()) {
            throw new BusinessException("current role records are required before salary calculation");
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            BigDecimal totalSalaryAmount = BigDecimal.ZERO;
            for (OrderRoleRecord roleRecord : roleRecords) {
                SalaryRule rule = salaryRuleMapper.selectOne(new LambdaQueryWrapper<SalaryRule>()
                        .eq(SalaryRule::getRoleCode, roleRecord.getRoleCode())
                        .eq(SalaryRule::getIsActive, ACTIVE_FLAG)
                        .last("LIMIT 1"));
                if (rule == null) {
                    throw new BusinessException("active salary rule not found for role " + roleRecord.getRoleCode());
                }
                SalaryDetail detail = new SalaryDetail();
                detail.setPlanOrderId(planOrderId);
                detail.setUserId(roleRecord.getUserId());
                detail.setRoleCode(roleRecord.getRoleCode());
                detail.setOrderAmount(scaleMoney(order.getAmount()));
                detail.setAmount(calculateAmount(order.getAmount(), rule));
                detail.setCreateTime(now);
                if (salaryDetailMapper.insert(detail) <= 0) {
                    throw new BusinessException("failed to create salary detail");
                }
                totalSalaryAmount = totalSalaryAmount.add(detail.getAmount());
                financeService.recordSalaryIncome(detail);
            }
            riskControlService.validateSplitTotalNotExceedOrderAmount(order.getAmount(), totalSalaryAmount);
            idempotentService.markSuccess(bizKey);
            return salaryDetailMapper.selectList(new LambdaQueryWrapper<SalaryDetail>()
                    .eq(SalaryDetail::getPlanOrderId, planOrderId)
                    .orderByAsc(SalaryDetail::getCreateTime, SalaryDetail::getId));
        } catch (RuntimeException exception) {
            idempotentService.markFail(bizKey);
            throw exception;
        }
    }

    private PlanOrder getPlanOrder(Long planOrderId) {
        PlanOrder planOrder = planOrderMapper.selectById(planOrderId);
        if (planOrder == null) {
            throw new BusinessException("plan order not found");
        }
        return planOrder;
    }

    private void ensurePlanOrderFinished(PlanOrder planOrder) {
        if (!PlanOrderStatus.FINISHED.name().equals(planOrder.getStatus())) {
            throw new BusinessException("plan order must be finished before salary calculation");
        }
    }

    private BigDecimal calculateAmount(BigDecimal orderAmount, SalaryRule rule) {
        BigDecimal ruleValue = rule.getRuleValue();
        if (ruleValue == null || ruleValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("salary rule value must be non-negative");
        }
        return switch (SalaryRuleType.fromCode(rule.getRuleType())) {
            case PERCENT -> scaleMoney(orderAmount.multiply(ruleValue));
            case FIXED -> scaleMoney(ruleValue);
        };
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId is required");
        }
    }

    private void validatePlanOrderId(Long planOrderId) {
        if (planOrderId == null || planOrderId <= 0) {
            throw new BusinessException("planOrderId is required");
        }
    }

    private Long defaultCount(Long value) {
        return value == null ? 0L : value;
    }

    private Map<String, Long> buildRoleDistribution(List<Map<String, Object>> rawRows) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        if (rawRows == null) {
            return distribution;
        }
        for (Map<String, Object> row : rawRows) {
            Object roleCode = row.get("roleCode");
            Object roleCount = row.get("roleCount");
            if (roleCode != null) {
                distribution.put(String.valueOf(roleCode), toLong(roleCount));
            }
        }
        return distribution;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer integerValue) {
            return integerValue.longValue();
        }
        if (value instanceof BigInteger bigIntegerValue) {
            return bigIntegerValue.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private BigDecimal sumSalaryDetails(List<SalaryDetail> details) {
        return details.stream()
                .map(SalaryDetail::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumSettlements(List<SalarySettlement> settlements) {
        return settlements.stream()
                .map(SalarySettlement::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumWithdraws(List<WithdrawRecord> withdrawRecords) {
        return withdrawRecords.stream()
                .map(WithdrawRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
