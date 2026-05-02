package com.seedcrm.crm.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorWithdrawMapper;
import com.seedcrm.crm.distributor.enums.DistributorWithdrawStatus;
import com.seedcrm.crm.finance.dto.FinanceBalanceResponse;
import com.seedcrm.crm.finance.dto.FinanceCheckItemResponse;
import com.seedcrm.crm.finance.dto.FinanceCheckResponse;
import com.seedcrm.crm.finance.entity.Account;
import com.seedcrm.crm.finance.entity.FinanceCheckRecord;
import com.seedcrm.crm.finance.entity.Ledger;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.enums.FinanceCheckStatus;
import com.seedcrm.crm.finance.enums.LedgerBizType;
import com.seedcrm.crm.finance.enums.LedgerDirection;
import com.seedcrm.crm.finance.mapper.FinanceCheckRecordMapper;
import com.seedcrm.crm.finance.service.AccountService;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.finance.service.LedgerService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import com.seedcrm.crm.salary.enums.WithdrawStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceServiceImpl implements FinanceService {

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final FinanceCheckRecordMapper financeCheckRecordMapper;
    private final OrderMapper orderMapper;
    private final SalaryDetailMapper salaryDetailMapper;
    private final DistributorIncomeDetailMapper distributorIncomeDetailMapper;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final DistributorWithdrawMapper distributorWithdrawMapper;

    public FinanceServiceImpl(AccountService accountService,
                              LedgerService ledgerService,
                              FinanceCheckRecordMapper financeCheckRecordMapper,
                              OrderMapper orderMapper,
                              SalaryDetailMapper salaryDetailMapper,
                              DistributorIncomeDetailMapper distributorIncomeDetailMapper,
                              WithdrawRecordMapper withdrawRecordMapper,
                              DistributorWithdrawMapper distributorWithdrawMapper) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.financeCheckRecordMapper = financeCheckRecordMapper;
        this.orderMapper = orderMapper;
        this.salaryDetailMapper = salaryDetailMapper;
        this.distributorIncomeDetailMapper = distributorIncomeDetailMapper;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.distributorWithdrawMapper = distributorWithdrawMapper;
    }

    @Override
    @Transactional
    public Ledger recordOrderIncome(Order order) {
        if (order == null || order.getId() == null) {
            throw new BusinessException("completed order is required");
        }
        if (order.getAmount() == null || order.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("order amount must be greater than 0");
        }
        return ledgerService.record(AccountOwnerType.PLATFORM, AccountService.PLATFORM_OWNER_ID, order.getAmount(),
                LedgerBizType.ORDER, order.getId(), LedgerDirection.IN);
    }

    @Override
    @Transactional
    public Ledger recordSalaryIncome(SalaryDetail salaryDetail) {
        if (salaryDetail == null || salaryDetail.getId() == null) {
            throw new BusinessException("salary detail is required");
        }
        if (salaryDetail.getUserId() == null || salaryDetail.getUserId() <= 0) {
            throw new BusinessException("salary detail userId is required");
        }
        BigDecimal amount = salaryDetail.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("salary detail amount is required");
        }
        LedgerDirection direction = amount.compareTo(BigDecimal.ZERO) < 0
                ? LedgerDirection.OUT
                : LedgerDirection.IN;
        return ledgerService.record(AccountOwnerType.USER, salaryDetail.getUserId(), amount.abs(),
                LedgerBizType.SALARY, salaryDetail.getId(), direction);
    }

    @Override
    @Transactional
    public Ledger recordDistributorIncome(DistributorIncomeDetail incomeDetail) {
        if (incomeDetail == null || incomeDetail.getId() == null) {
            throw new BusinessException("distributor income detail is required");
        }
        if (incomeDetail.getDistributorId() == null || incomeDetail.getDistributorId() <= 0) {
            throw new BusinessException("distributorId is required");
        }
        return ledgerService.record(AccountOwnerType.DISTRIBUTOR, incomeDetail.getDistributorId(),
                incomeDetail.getIncomeAmount(), LedgerBizType.DISTRIBUTOR, incomeDetail.getId(), LedgerDirection.IN);
    }

    @Override
    @Transactional
    public Ledger recordUserWithdraw(WithdrawRecord withdrawRecord) {
        if (withdrawRecord == null || withdrawRecord.getId() == null) {
            throw new BusinessException("withdraw record is required");
        }
        if (withdrawRecord.getUserId() == null || withdrawRecord.getUserId() <= 0) {
            throw new BusinessException("withdraw record userId is required");
        }
        return ledgerService.record(AccountOwnerType.USER, withdrawRecord.getUserId(), withdrawRecord.getAmount(),
                LedgerBizType.WITHDRAW, withdrawRecord.getId(), LedgerDirection.OUT);
    }

    @Override
    @Transactional
    public Ledger recordDistributorWithdraw(DistributorWithdraw withdrawRecord) {
        if (withdrawRecord == null || withdrawRecord.getId() == null) {
            throw new BusinessException("distributor withdraw record is required");
        }
        if (withdrawRecord.getDistributorId() == null || withdrawRecord.getDistributorId() <= 0) {
            throw new BusinessException("distributor withdraw distributorId is required");
        }
        return ledgerService.record(AccountOwnerType.DISTRIBUTOR, withdrawRecord.getDistributorId(),
                withdrawRecord.getAmount(), LedgerBizType.WITHDRAW, withdrawRecord.getId(), LedgerDirection.OUT);
    }

    @Override
    public FinanceBalanceResponse getBalance(AccountOwnerType ownerType, Long ownerId) {
        Account account = accountService.getOrCreateAccount(ownerType, ownerId);
        return new FinanceBalanceResponse(account.getId(), account.getOwnerType(), account.getOwnerId(),
                ledgerService.getBalance(account.getId()));
    }

    @Override
    @Transactional
    public FinanceCheckResponse check() {
        synchronizeLedger();

        List<FinanceCheckItemResponse> records = new ArrayList<>();

        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .isNotNull(Order::getCompleteTime)
                .orderByAsc(Order::getCompleteTime, Order::getId));
        for (Order order : orders) {
            records.add(checkAndSave(LedgerBizType.ORDER, order.getId(), order.getAmount(),
                    AccountOwnerType.PLATFORM, AccountService.PLATFORM_OWNER_ID));
        }

        List<SalaryDetail> salaryDetails = salaryDetailMapper.selectList(new LambdaQueryWrapper<SalaryDetail>()
                .orderByAsc(SalaryDetail::getCreateTime, SalaryDetail::getId));
        for (SalaryDetail salaryDetail : salaryDetails) {
            records.add(checkAndSave(LedgerBizType.SALARY, salaryDetail.getId(), salaryDetail.getAmount(),
                    AccountOwnerType.USER, salaryDetail.getUserId()));
        }

        List<DistributorIncomeDetail> distributorIncomeDetails = distributorIncomeDetailMapper.selectList(
                new LambdaQueryWrapper<DistributorIncomeDetail>()
                        .orderByAsc(DistributorIncomeDetail::getCreateTime, DistributorIncomeDetail::getId));
        for (DistributorIncomeDetail incomeDetail : distributorIncomeDetails) {
            records.add(checkAndSave(LedgerBizType.DISTRIBUTOR, incomeDetail.getId(), incomeDetail.getIncomeAmount(),
                    AccountOwnerType.DISTRIBUTOR, incomeDetail.getDistributorId()));
        }

        int matchCount = (int) records.stream()
                .filter(record -> FinanceCheckStatus.MATCH.name().equals(record.getStatus()))
                .count();
        return new FinanceCheckResponse(records.size(), matchCount, records.size() - matchCount, records);
    }

    @Override
    public boolean hasLedgerRecord(LedgerBizType bizType, Long bizId) {
        return ledgerService.hasRecord(bizType, bizId);
    }

    private void synchronizeLedger() {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .isNotNull(Order::getCompleteTime)
                .orderByAsc(Order::getCompleteTime, Order::getId));
        orders.forEach(this::recordOrderIncome);

        List<SalaryDetail> salaryDetails = salaryDetailMapper.selectList(new LambdaQueryWrapper<SalaryDetail>()
                .orderByAsc(SalaryDetail::getCreateTime, SalaryDetail::getId));
        salaryDetails.forEach(this::recordSalaryIncome);

        List<DistributorIncomeDetail> distributorIncomeDetails = distributorIncomeDetailMapper.selectList(
                new LambdaQueryWrapper<DistributorIncomeDetail>()
                        .orderByAsc(DistributorIncomeDetail::getCreateTime, DistributorIncomeDetail::getId));
        distributorIncomeDetails.forEach(this::recordDistributorIncome);

        List<WithdrawRecord> withdrawRecords = withdrawRecordMapper.selectList(new LambdaQueryWrapper<WithdrawRecord>()
                .in(WithdrawRecord::getStatus, WithdrawStatus.APPROVED.name(), WithdrawStatus.PAID.name())
                .orderByAsc(WithdrawRecord::getCreateTime, WithdrawRecord::getId));
        withdrawRecords.forEach(this::recordUserWithdraw);

        List<DistributorWithdraw> distributorWithdraws = distributorWithdrawMapper.selectList(
                new LambdaQueryWrapper<DistributorWithdraw>()
                        .in(DistributorWithdraw::getStatus,
                                DistributorWithdrawStatus.APPROVED.name(),
                                DistributorWithdrawStatus.PAID.name())
                        .orderByAsc(DistributorWithdraw::getCreateTime, DistributorWithdraw::getId));
        distributorWithdraws.forEach(this::recordDistributorWithdraw);
    }

    private FinanceCheckItemResponse checkAndSave(LedgerBizType bizType,
                                                  Long bizId,
                                                  BigDecimal expectedAmount,
                                                  AccountOwnerType ownerType,
                                                  Long ownerId) {
        BigDecimal normalizedExpectedAmount = scale(expectedAmount);
        Account account = accountService.getOrCreateAccount(ownerType, ownerId);
        BigDecimal actualAmount = scale(ledgerService.getBizAmount(account.getId(), bizType, bizId));
        FinanceCheckStatus status = normalizedExpectedAmount.compareTo(actualAmount) == 0
                ? FinanceCheckStatus.MATCH
                : FinanceCheckStatus.MISMATCH;

        FinanceCheckRecord record = new FinanceCheckRecord();
        record.setBizType(bizType.name());
        record.setBizId(bizId);
        record.setExpectedAmount(normalizedExpectedAmount);
        record.setActualAmount(actualAmount);
        record.setStatus(status.name());
        record.setCreateTime(LocalDateTime.now());
        if (financeCheckRecordMapper.insert(record) <= 0) {
            throw new BusinessException("failed to create finance check record");
        }

        return new FinanceCheckItemResponse(bizType.name(), bizId, normalizedExpectedAmount, actualAmount, status.name());
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
