package com.seedcrm.crm.finance.service.impl;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.finance.entity.Account;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.mapper.AccountMapper;
import com.seedcrm.crm.finance.mapper.LedgerMapper;
import com.seedcrm.crm.finance.service.AccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountMapper accountMapper;
    private final LedgerMapper ledgerMapper;

    public AccountServiceImpl(AccountMapper accountMapper, LedgerMapper ledgerMapper) {
        this.accountMapper = accountMapper;
        this.ledgerMapper = ledgerMapper;
    }

    @Override
    @Transactional
    public Account getOrCreateAccount(AccountOwnerType ownerType, Long ownerId) {
        AccountOwnerType validatedOwnerType = requireOwnerType(ownerType);
        Long normalizedOwnerId = normalizeOwnerId(validatedOwnerType, ownerId);
        Account existing = accountMapper.selectByOwner(validatedOwnerType.name(), normalizedOwnerId);
        if (existing != null) {
            return existing;
        }

        Account account = new Account();
        account.setOwnerType(validatedOwnerType.name());
        account.setOwnerId(normalizedOwnerId);
        account.setCreateTime(LocalDateTime.now());
        try {
            if (accountMapper.insert(account) <= 0) {
                throw new BusinessException("failed to create account");
            }
            return account;
        } catch (DuplicateKeyException exception) {
            Account duplicated = accountMapper.selectByOwner(validatedOwnerType.name(), normalizedOwnerId);
            if (duplicated != null) {
                return duplicated;
            }
            throw exception;
        }
    }

    @Override
    public Account lockAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new BusinessException("accountId is required");
        }
        Account account = accountMapper.selectByIdForUpdate(accountId);
        if (account == null) {
            throw new BusinessException("account not found");
        }
        return account;
    }

    @Override
    public BigDecimal getBalance(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new BusinessException("accountId is required");
        }
        BigDecimal balance = ledgerMapper.sumChangeAmountByAccountId(accountId);
        if (balance == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return balance.setScale(2, RoundingMode.HALF_UP);
    }

    private AccountOwnerType requireOwnerType(AccountOwnerType ownerType) {
        if (ownerType == null) {
            throw new BusinessException("ownerType is required");
        }
        return ownerType;
    }

    private Long normalizeOwnerId(AccountOwnerType ownerType, Long ownerId) {
        if (ownerType == AccountOwnerType.PLATFORM) {
            return PLATFORM_OWNER_ID;
        }
        if (ownerId == null || ownerId <= 0) {
            throw new BusinessException("ownerId is required");
        }
        return ownerId;
    }
}
