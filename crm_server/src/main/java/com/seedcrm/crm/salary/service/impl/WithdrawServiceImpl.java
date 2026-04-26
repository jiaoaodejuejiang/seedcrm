package com.seedcrm.crm.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.risk.enums.IdempotentBizType;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.risk.service.IdempotentService;
import com.seedcrm.crm.risk.service.RiskControlService;
import com.seedcrm.crm.salary.dto.WithdrawCreateRequest;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.enums.WithdrawStatus;
import com.seedcrm.crm.salary.mapper.SalarySettlementMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import com.seedcrm.crm.salary.service.WithdrawService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawServiceImpl implements WithdrawService {

    private final SalarySettlementMapper salarySettlementMapper;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final FinanceService financeService;
    private final DbLockService dbLockService;
    private final IdempotentService idempotentService;
    private final RiskControlService riskControlService;

    public WithdrawServiceImpl(SalarySettlementMapper salarySettlementMapper,
                               WithdrawRecordMapper withdrawRecordMapper,
                               FinanceService financeService,
                               DbLockService dbLockService,
                               IdempotentService idempotentService,
                               RiskControlService riskControlService) {
        this.salarySettlementMapper = salarySettlementMapper;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.financeService = financeService;
        this.dbLockService = dbLockService;
        this.idempotentService = idempotentService;
        this.riskControlService = riskControlService;
    }

    @Override
    public BigDecimal getWithdrawableAmount(Long userId) {
        validateUserId(userId);
        BigDecimal totalSettledAmount = sumSettlements(salarySettlementMapper.selectList(new LambdaQueryWrapper<SalarySettlement>()
                .eq(SalarySettlement::getUserId, userId)));
        BigDecimal totalWithdrawAmount = sumWithdraws(withdrawRecordMapper.selectList(new LambdaQueryWrapper<WithdrawRecord>()
                .eq(WithdrawRecord::getUserId, userId)));
        BigDecimal withdrawableAmount = totalSettledAmount.subtract(totalWithdrawAmount);
        if (withdrawableAmount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return withdrawableAmount.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public WithdrawRecord createWithdraw(WithdrawCreateRequest request) {
        if (request == null) {
            throw new BusinessException("request body is required");
        }
        validateUserId(request.getUserId());
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("withdraw amount must be greater than 0");
        }
        dbLockService.lockAccount(AccountOwnerType.USER, request.getUserId());
        BigDecimal withdrawableAmount = getWithdrawableAmount(request.getUserId());
        BigDecimal requestAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        riskControlService.validateWithdrawAmountNotExceedBalance(requestAmount, withdrawableAmount);

        WithdrawRecord withdrawRecord = new WithdrawRecord();
        withdrawRecord.setUserId(request.getUserId());
        withdrawRecord.setAmount(requestAmount);
        withdrawRecord.setStatus(WithdrawStatus.PENDING.name());
        withdrawRecord.setCreateTime(LocalDateTime.now());
        if (withdrawRecordMapper.insert(withdrawRecord) <= 0) {
            throw new BusinessException("failed to create withdraw record");
        }
        return withdrawRecord;
    }

    @Override
    @Transactional
    public WithdrawRecord approveWithdraw(Long withdrawId, WithdrawStatus targetStatus) {
        if (withdrawId == null || withdrawId <= 0) {
            throw new BusinessException("withdrawId is required");
        }
        if (targetStatus == null) {
            throw new BusinessException("target withdraw status is required");
        }
        if (targetStatus == WithdrawStatus.PENDING) {
            throw new BusinessException("withdraw status cannot be set back to PENDING");
        }
        WithdrawRecord withdrawRecord = dbLockService.lockWithdrawRecord(withdrawId);
        dbLockService.lockAccount(AccountOwnerType.USER, withdrawRecord.getUserId());

        WithdrawStatus currentStatus = WithdrawStatus.fromCode(withdrawRecord.getStatus());
        ensureStatusTransition(currentStatus, targetStatus);
        String bizKey = "WITHDRAW_USER_" + withdrawId;
        if ((targetStatus == WithdrawStatus.APPROVED || targetStatus == WithdrawStatus.PAID)
                && !idempotentService.tryStart(bizKey, IdempotentBizType.WITHDRAW)) {
            return withdrawRecord;
        }
        withdrawRecord.setStatus(targetStatus.name());
        try {
            if (withdrawRecordMapper.updateById(withdrawRecord) <= 0) {
                throw new BusinessException("failed to update withdraw status");
            }
            if (targetStatus == WithdrawStatus.APPROVED || targetStatus == WithdrawStatus.PAID) {
                financeService.recordUserWithdraw(withdrawRecord);
                idempotentService.markSuccess(bizKey);
            }
            return withdrawRecord;
        } catch (RuntimeException exception) {
            if (targetStatus == WithdrawStatus.APPROVED || targetStatus == WithdrawStatus.PAID) {
                idempotentService.markFail(bizKey);
            }
            throw exception;
        }
    }

    @Override
    public List<WithdrawRecord> listWithdraws(Long userId) {
        LambdaQueryWrapper<WithdrawRecord> wrapper = new LambdaQueryWrapper<WithdrawRecord>()
                .orderByDesc(WithdrawRecord::getCreateTime)
                .orderByDesc(WithdrawRecord::getId);
        if (userId != null && userId > 0) {
            wrapper.eq(WithdrawRecord::getUserId, userId);
        }
        return withdrawRecordMapper.selectList(wrapper);
    }

    private void ensureStatusTransition(WithdrawStatus currentStatus, WithdrawStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return;
        }
        if (currentStatus == WithdrawStatus.PENDING
                && (targetStatus == WithdrawStatus.APPROVED || targetStatus == WithdrawStatus.PAID)) {
            return;
        }
        if (currentStatus == WithdrawStatus.APPROVED && targetStatus == WithdrawStatus.PAID) {
            return;
        }
        throw new BusinessException("invalid withdraw status transition: "
                + currentStatus.name() + " -> " + targetStatus.name());
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId is required");
        }
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
}
