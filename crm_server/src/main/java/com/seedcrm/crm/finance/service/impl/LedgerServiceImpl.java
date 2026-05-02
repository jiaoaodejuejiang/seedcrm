package com.seedcrm.crm.finance.service.impl;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.finance.entity.Account;
import com.seedcrm.crm.finance.entity.Ledger;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.enums.LedgerBizType;
import com.seedcrm.crm.finance.enums.LedgerDirection;
import com.seedcrm.crm.finance.mapper.LedgerMapper;
import com.seedcrm.crm.finance.service.AccountService;
import com.seedcrm.crm.finance.service.LedgerService;
import com.seedcrm.crm.risk.service.RiskControlService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServiceImpl implements LedgerService {

    private final AccountService accountService;
    private final LedgerMapper ledgerMapper;
    private final RiskControlService riskControlService;

    public LedgerServiceImpl(AccountService accountService,
                             LedgerMapper ledgerMapper,
                             RiskControlService riskControlService) {
        this.accountService = accountService;
        this.ledgerMapper = ledgerMapper;
        this.riskControlService = riskControlService;
    }

    @Override
    @Transactional
    public Ledger record(AccountOwnerType ownerType,
                         Long ownerId,
                         BigDecimal amount,
                         LedgerBizType bizType,
                         Long bizId,
                         LedgerDirection direction) {
        validateAmount(amount);
        validateBiz(bizType, bizId);
        if (direction == null) {
            throw new BusinessException("direction is required");
        }

        Account account = accountService.getOrCreateAccount(ownerType, ownerId);
        Ledger existing = ledgerMapper.selectByAccountAndBiz(account.getId(), bizType.name(), bizId);
        if (existing != null) {
            return existing;
        }

        accountService.lockAccount(account.getId());
        existing = ledgerMapper.selectByAccountAndBiz(account.getId(), bizType.name(), bizId);
        if (existing != null) {
            return existing;
        }

        BigDecimal normalizedAmount = normalizeChangeAmount(amount, direction);
        BigDecimal balanceBefore = zeroIfNull(ledgerMapper.sumChangeAmountByAccountId(account.getId()));
        if (direction == LedgerDirection.OUT && bizType == LedgerBizType.WITHDRAW) {
            riskControlService.validateWithdrawAmountNotExceedBalance(normalizedAmount.abs(), balanceBefore);
        }
        BigDecimal balanceAfter = balanceBefore.add(normalizedAmount).setScale(2, RoundingMode.HALF_UP);

        Ledger ledger = new Ledger();
        ledger.setAccountId(account.getId());
        ledger.setChangeAmount(normalizedAmount);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setBizType(bizType.name());
        ledger.setBizId(bizId);
        ledger.setDirection(direction.name());
        ledger.setCreateTime(LocalDateTime.now());
        try {
            if (ledgerMapper.insert(ledger) <= 0) {
                throw new BusinessException("failed to create ledger");
            }
            return ledger;
        } catch (DuplicateKeyException exception) {
            Ledger duplicate = ledgerMapper.selectByAccountAndBiz(account.getId(), bizType.name(), bizId);
            if (duplicate != null) {
                return duplicate;
            }
            throw exception;
        }
    }

    @Override
    public BigDecimal getBalance(Long accountId) {
        return accountService.getBalance(accountId);
    }

    @Override
    public BigDecimal getBizAmount(Long accountId, LedgerBizType bizType, Long bizId) {
        validateBiz(bizType, bizId);
        if (accountId == null || accountId <= 0) {
            throw new BusinessException("accountId is required");
        }
        BigDecimal amount = ledgerMapper.sumChangeAmountByAccountAndBiz(accountId, bizType.name(), bizId);
        return zeroIfNull(amount);
    }

    @Override
    public boolean hasRecord(LedgerBizType bizType, Long bizId) {
        validateBiz(bizType, bizId);
        return ledgerMapper.selectByBiz(bizType.name(), bizId) != null;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("amount must be greater than 0");
        }
    }

    private void validateBiz(LedgerBizType bizType, Long bizId) {
        if (bizType == null) {
            throw new BusinessException("bizType is required");
        }
        if (bizId == null || bizId <= 0) {
            throw new BusinessException("bizId is required");
        }
    }

    private BigDecimal normalizeChangeAmount(BigDecimal amount, LedgerDirection direction) {
        BigDecimal scaledAmount = amount.setScale(2, RoundingMode.HALF_UP);
        if (direction == LedgerDirection.IN) {
            return scaledAmount.abs();
        }
        return scaledAmount.abs().negate();
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
