package com.seedcrm.crm.finance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.mapper.DistributorWithdrawMapper;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.finance.dto.FinanceBalanceResponse;
import com.seedcrm.crm.finance.dto.FinanceCheckResponse;
import com.seedcrm.crm.finance.entity.Account;
import com.seedcrm.crm.finance.entity.FinanceCheckRecord;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.enums.LedgerBizType;
import com.seedcrm.crm.finance.enums.LedgerDirection;
import com.seedcrm.crm.finance.service.AccountService;
import com.seedcrm.crm.finance.service.LedgerService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceServiceImplTest {

    @Mock
    private AccountService accountService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private com.seedcrm.crm.finance.mapper.FinanceCheckRecordMapper financeCheckRecordMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private SalaryDetailMapper salaryDetailMapper;

    @Mock
    private DistributorIncomeDetailMapper distributorIncomeDetailMapper;

    @Mock
    private WithdrawRecordMapper withdrawRecordMapper;

    @Mock
    private DistributorWithdrawMapper distributorWithdrawMapper;

    private FinanceServiceImpl financeService;

    @BeforeEach
    void setUp() {
        financeService = new FinanceServiceImpl(accountService, ledgerService, financeCheckRecordMapper,
                orderMapper, salaryDetailMapper, distributorIncomeDetailMapper,
                withdrawRecordMapper, distributorWithdrawMapper);
    }

    @Test
    void getBalanceShouldReturnLedgerBalanceForOwnerAccount() {
        Account account = new Account();
        account.setId(3L);
        account.setOwnerType(AccountOwnerType.USER.name());
        account.setOwnerId(9L);
        when(accountService.getOrCreateAccount(AccountOwnerType.USER, 9L)).thenReturn(account);
        when(ledgerService.getBalance(3L)).thenReturn(new BigDecimal("188.00"));

        FinanceBalanceResponse response = financeService.getBalance(AccountOwnerType.USER, 9L);

        assertThat(response.getAccountId()).isEqualTo(3L);
        assertThat(response.getBalance()).isEqualByComparingTo("188.00");
    }

    @Test
    void recordSalaryIncomeShouldRecordRefundReversalAsOutLedger() {
        SalaryDetail salaryDetail = new SalaryDetail();
        salaryDetail.setId(22L);
        salaryDetail.setUserId(9L);
        salaryDetail.setAmount(new BigDecimal("-35.00"));

        financeService.recordSalaryIncome(salaryDetail);

        verify(ledgerService).record(
                eq(AccountOwnerType.USER),
                eq(9L),
                argThat(amount -> amount.compareTo(new BigDecimal("35.00")) == 0),
                eq(LedgerBizType.SALARY),
                eq(22L),
                eq(LedgerDirection.OUT));
    }

    @Test
    void checkShouldPersistMatchAndMismatchRecords() {
        Order order = new Order();
        order.setId(1L);
        order.setAmount(new BigDecimal("300.00"));
        order.setCompleteTime(LocalDateTime.now());
        when(orderMapper.selectList(any())).thenReturn(List.of(order));

        SalaryDetail salaryDetail = new SalaryDetail();
        salaryDetail.setId(2L);
        salaryDetail.setUserId(9L);
        salaryDetail.setAmount(new BigDecimal("100.00"));
        when(salaryDetailMapper.selectList(any())).thenReturn(List.of(salaryDetail));

        DistributorIncomeDetail incomeDetail = new DistributorIncomeDetail();
        incomeDetail.setId(3L);
        incomeDetail.setDistributorId(7L);
        incomeDetail.setIncomeAmount(new BigDecimal("60.00"));
        when(distributorIncomeDetailMapper.selectList(any())).thenReturn(List.of(incomeDetail));
        when(withdrawRecordMapper.selectList(any())).thenReturn(List.of());
        when(distributorWithdrawMapper.selectList(any())).thenReturn(List.of());

        Account platformAccount = new Account();
        platformAccount.setId(11L);
        platformAccount.setOwnerType(AccountOwnerType.PLATFORM.name());
        platformAccount.setOwnerId(0L);
        Account userAccount = new Account();
        userAccount.setId(12L);
        userAccount.setOwnerType(AccountOwnerType.USER.name());
        userAccount.setOwnerId(9L);
        Account distributorAccount = new Account();
        distributorAccount.setId(13L);
        distributorAccount.setOwnerType(AccountOwnerType.DISTRIBUTOR.name());
        distributorAccount.setOwnerId(7L);

        when(accountService.getOrCreateAccount(AccountOwnerType.PLATFORM, 0L)).thenReturn(platformAccount);
        when(accountService.getOrCreateAccount(AccountOwnerType.USER, 9L)).thenReturn(userAccount);
        when(accountService.getOrCreateAccount(AccountOwnerType.DISTRIBUTOR, 7L)).thenReturn(distributorAccount);

        when(ledgerService.getBizAmount(11L, com.seedcrm.crm.finance.enums.LedgerBizType.ORDER, 1L))
                .thenReturn(new BigDecimal("300.00"));
        when(ledgerService.getBizAmount(12L, com.seedcrm.crm.finance.enums.LedgerBizType.SALARY, 2L))
                .thenReturn(new BigDecimal("80.00"));
        when(ledgerService.getBizAmount(13L, com.seedcrm.crm.finance.enums.LedgerBizType.DISTRIBUTOR, 3L))
                .thenReturn(new BigDecimal("60.00"));
        when(ledgerService.record(any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(financeCheckRecordMapper.insert(any(FinanceCheckRecord.class))).thenReturn(1);

        FinanceCheckResponse response = financeService.check();

        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getMatchCount()).isEqualTo(2);
        assertThat(response.getMismatchCount()).isEqualTo(1);

        ArgumentCaptor<FinanceCheckRecord> captor = ArgumentCaptor.forClass(FinanceCheckRecord.class);
        verify(financeCheckRecordMapper, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(FinanceCheckRecord::getStatus)
                .containsExactly("MATCH", "MISMATCH", "MATCH");
    }

    @Test
    void checkShouldMatchNegativeSalaryReversalLedger() {
        when(orderMapper.selectList(any())).thenReturn(List.of());
        SalaryDetail salaryDetail = new SalaryDetail();
        salaryDetail.setId(44L);
        salaryDetail.setUserId(9L);
        salaryDetail.setAmount(new BigDecimal("-40.00"));
        when(salaryDetailMapper.selectList(any())).thenReturn(List.of(salaryDetail));
        when(distributorIncomeDetailMapper.selectList(any())).thenReturn(List.of());
        when(withdrawRecordMapper.selectList(any())).thenReturn(List.of());
        when(distributorWithdrawMapper.selectList(any())).thenReturn(List.of());

        Account userAccount = new Account();
        userAccount.setId(12L);
        userAccount.setOwnerType(AccountOwnerType.USER.name());
        userAccount.setOwnerId(9L);
        when(accountService.getOrCreateAccount(AccountOwnerType.USER, 9L)).thenReturn(userAccount);
        when(ledgerService.getBizAmount(12L, LedgerBizType.SALARY, 44L))
                .thenReturn(new BigDecimal("-40.00"));
        when(financeCheckRecordMapper.insert(any(FinanceCheckRecord.class))).thenReturn(1);

        FinanceCheckResponse response = financeService.check();

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getMatchCount()).isEqualTo(1);
        verify(ledgerService).record(
                eq(AccountOwnerType.USER),
                eq(9L),
                argThat(amount -> amount.compareTo(new BigDecimal("40.00")) == 0),
                eq(LedgerBizType.SALARY),
                eq(44L),
                eq(LedgerDirection.OUT));
    }
}
