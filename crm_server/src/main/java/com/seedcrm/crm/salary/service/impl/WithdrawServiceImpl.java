package com.seedcrm.crm.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.risk.enums.IdempotentBizType;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.risk.service.IdempotentService;
import com.seedcrm.crm.risk.service.RiskControlService;
import com.seedcrm.crm.salary.dto.SalarySettlementPolicyDtos;
import com.seedcrm.crm.salary.dto.WithdrawCreateRequest;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.enums.WithdrawStatus;
import com.seedcrm.crm.salary.mapper.SalarySettlementMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import com.seedcrm.crm.salary.service.SalarySettlementPolicyService;
import com.seedcrm.crm.salary.service.WithdrawService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WithdrawServiceImpl implements WithdrawService {

    private static final String SUBJECT_INTERNAL_STAFF = "INTERNAL_STAFF";
    private static final String SUBJECT_DISTRIBUTOR = "DISTRIBUTOR";
    private static final String MODE_LEDGER_ONLY = "LEDGER_ONLY";
    private static final String MODE_WITHDRAW_DIRECT = "WITHDRAW_DIRECT";
    private static final String MODE_WITHDRAW_AUDIT = "WITHDRAW_AUDIT";

    private final SalarySettlementMapper salarySettlementMapper;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final SalarySettlementPolicyService salarySettlementPolicyService;
    private final FinanceService financeService;
    private final DbLockService dbLockService;
    private final IdempotentService idempotentService;
    private final RiskControlService riskControlService;

    public WithdrawServiceImpl(SalarySettlementMapper salarySettlementMapper,
                               WithdrawRecordMapper withdrawRecordMapper,
                               SalarySettlementPolicyService salarySettlementPolicyService,
                               FinanceService financeService,
                               DbLockService dbLockService,
                               IdempotentService idempotentService,
                               RiskControlService riskControlService) {
        this.salarySettlementMapper = salarySettlementMapper;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.salarySettlementPolicyService = salarySettlementPolicyService;
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
        BigDecimal requestAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        SalarySettlementPolicyDtos.SimulateResponse policy = resolveWithdrawPolicy(request, requestAmount);
        if (!policy.isMatched()) {
            throw new BusinessException("未命中已发布提现规则，请先在结算配置中维护规则");
        }
        String settlementMode = normalize(policy.getSettlementMode());
        if (MODE_LEDGER_ONLY.equals(settlementMode)) {
            throw new BusinessException("当前结算规则为只记账，不允许发起提现");
        }
        if (!MODE_WITHDRAW_DIRECT.equals(settlementMode) && !MODE_WITHDRAW_AUDIT.equals(settlementMode)) {
            throw new BusinessException("当前结算规则不支持提现方式: " + settlementMode);
        }
        dbLockService.lockAccount(AccountOwnerType.USER, request.getUserId());
        BigDecimal withdrawableAmount = getWithdrawableAmount(request.getUserId());
        riskControlService.validateWithdrawAmountNotExceedBalance(requestAmount, withdrawableAmount);

        WithdrawRecord withdrawRecord = new WithdrawRecord();
        withdrawRecord.setUserId(request.getUserId());
        withdrawRecord.setAmount(requestAmount);
        withdrawRecord.setSubjectType(policy.getSubjectType());
        withdrawRecord.setSettlementMode(settlementMode);
        withdrawRecord.setAuditRequired(MODE_WITHDRAW_AUDIT.equals(settlementMode) ? 1 : 0);
        LocalDateTime now = LocalDateTime.now();
        withdrawRecord.setStatus(MODE_WITHDRAW_DIRECT.equals(settlementMode)
                ? WithdrawStatus.PAID.name()
                : WithdrawStatus.PENDING.name());
        withdrawRecord.setCreateTime(now);
        if (MODE_WITHDRAW_DIRECT.equals(settlementMode)) {
            withdrawRecord.setApproveTime(now);
            withdrawRecord.setPaidTime(now);
        }
        if (withdrawRecordMapper.insert(withdrawRecord) <= 0) {
            throw new BusinessException("failed to create withdraw record");
        }
        if (MODE_WITHDRAW_DIRECT.equals(settlementMode)) {
            recordFinanceWithdraw(withdrawRecord);
        }
        return withdrawRecord;
    }

    @Override
    @Transactional
    public WithdrawRecord approveWithdraw(Long withdrawId, WithdrawStatus targetStatus, String auditRemark) {
        if (withdrawId == null || withdrawId <= 0) {
            throw new BusinessException("withdrawId is required");
        }
        if (targetStatus == null) {
            throw new BusinessException("target withdraw status is required");
        }
        if (targetStatus == WithdrawStatus.PENDING) {
            throw new BusinessException("withdraw status cannot be set back to PENDING");
        }
        String normalizedAuditRemark = auditRemark == null ? "" : auditRemark.trim();
        if (targetStatus == WithdrawStatus.REJECTED && !StringUtils.hasText(normalizedAuditRemark)) {
            throw new BusinessException("驳回提现必须填写审核说明");
        }
        WithdrawRecord withdrawRecord = dbLockService.lockWithdrawRecord(withdrawId);
        dbLockService.lockAccount(AccountOwnerType.USER, withdrawRecord.getUserId());

        WithdrawStatus currentStatus = WithdrawStatus.fromCode(withdrawRecord.getStatus());
        ensureStatusTransition(currentStatus, targetStatus);
        String bizKey = "WITHDRAW_USER_" + withdrawId;
        if (targetStatus == WithdrawStatus.PAID
                && !idempotentService.tryStart(bizKey, IdempotentBizType.WITHDRAW)) {
            return withdrawRecord;
        }
        withdrawRecord.setStatus(targetStatus.name());
        LocalDateTime now = LocalDateTime.now();
        if (targetStatus == WithdrawStatus.APPROVED) {
            withdrawRecord.setApproveTime(now);
        }
        if (targetStatus == WithdrawStatus.REJECTED) {
            withdrawRecord.setApproveTime(now);
            withdrawRecord.setAuditRemark(normalizedAuditRemark);
        }
        if (targetStatus == WithdrawStatus.PAID) {
            if (withdrawRecord.getApproveTime() == null) {
                withdrawRecord.setApproveTime(now);
            }
            withdrawRecord.setPaidTime(now);
            if (StringUtils.hasText(normalizedAuditRemark)) {
                withdrawRecord.setAuditRemark(normalizedAuditRemark);
            }
        }
        try {
            if (withdrawRecordMapper.updateById(withdrawRecord) <= 0) {
                throw new BusinessException("failed to update withdraw status");
            }
            if (targetStatus == WithdrawStatus.PAID) {
                recordFinanceWithdraw(withdrawRecord);
            }
            if (targetStatus == WithdrawStatus.PAID) {
                idempotentService.markSuccess(bizKey);
            }
            return withdrawRecord;
        } catch (RuntimeException exception) {
            if (targetStatus == WithdrawStatus.PAID) {
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
                && (targetStatus == WithdrawStatus.APPROVED
                || targetStatus == WithdrawStatus.PAID
                || targetStatus == WithdrawStatus.REJECTED)) {
            return;
        }
        if (currentStatus == WithdrawStatus.APPROVED && targetStatus == WithdrawStatus.PAID) {
            return;
        }
        throw new BusinessException("invalid withdraw status transition: "
                + currentStatus.name() + " -> " + targetStatus.name());
    }

    private SalarySettlementPolicyDtos.SimulateResponse resolveWithdrawPolicy(WithdrawCreateRequest request,
                                                                              BigDecimal amount) {
        SalarySettlementPolicyDtos.SimulateRequest simulateRequest = new SalarySettlementPolicyDtos.SimulateRequest();
        String subjectType = StringUtils.hasText(request.getSubjectType()) ? request.getSubjectType() : SUBJECT_INTERNAL_STAFF;
        String normalizedSubjectType = normalize(subjectType);
        if (!SUBJECT_INTERNAL_STAFF.equals(normalizedSubjectType) && !SUBJECT_DISTRIBUTOR.equals(normalizedSubjectType)) {
            throw new BusinessException("unsupported withdraw subject type: " + subjectType);
        }
        simulateRequest.setSubjectType(normalizedSubjectType);
        simulateRequest.setRoleCode(request.getRoleCode());
        simulateRequest.setAmount(amount);
        return salarySettlementPolicyService.simulate(simulateRequest);
    }

    private void recordFinanceWithdraw(WithdrawRecord withdrawRecord) {
        financeService.recordUserWithdraw(withdrawRecord);
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
                .filter(record -> WithdrawStatus.fromCode(record.getStatus()) != WithdrawStatus.REJECTED)
                .map(WithdrawRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
