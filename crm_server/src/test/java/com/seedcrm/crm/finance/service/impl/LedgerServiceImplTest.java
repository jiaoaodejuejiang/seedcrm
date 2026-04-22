package com.seedcrm.crm.finance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.finance.entity.Account;
import com.seedcrm.crm.finance.entity.Ledger;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.enums.LedgerBizType;
import com.seedcrm.crm.finance.enums.LedgerDirection;
import com.seedcrm.crm.finance.mapper.LedgerMapper;
import com.seedcrm.crm.finance.service.AccountService;
import com.seedcrm.crm.risk.service.RiskControlService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

    @Mock
    private AccountService accountService;

    @Mock
    private LedgerMapper ledgerMapper;

    @Mock
    private RiskControlService riskControlService;

    private LedgerServiceImpl ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerServiceImpl(accountService, ledgerMapper, riskControlService);
    }

    @Test
    void recordShouldCreateInLedgerWithBalanceAfter() {
        Account account = new Account();
        account.setId(9L);
        when(accountService.getOrCreateAccount(AccountOwnerType.USER, 7L)).thenReturn(account);
        when(accountService.lockAccount(9L)).thenReturn(account);
        when(ledgerMapper.selectByAccountAndBiz(9L, LedgerBizType.SALARY.name(), 88L)).thenReturn(null, null);
        when(ledgerMapper.sumChangeAmountByAccountId(9L)).thenReturn(new BigDecimal("30.00"));
        when(ledgerMapper.insert(any(Ledger.class))).thenAnswer(invocation -> {
            Ledger ledger = invocation.getArgument(0);
            ledger.setId(101L);
            return 1;
        });

        Ledger ledger = ledgerService.record(AccountOwnerType.USER, 7L, new BigDecimal("20.00"),
                LedgerBizType.SALARY, 88L, LedgerDirection.IN);

        assertThat(ledger.getChangeAmount()).isEqualByComparingTo("20.00");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("50.00");
        assertThat(ledger.getDirection()).isEqualTo(LedgerDirection.IN.name());
    }

    @Test
    void recordShouldReturnExistingLedgerWhenDuplicateBizExists() {
        Account account = new Account();
        account.setId(9L);
        Ledger existing = new Ledger();
        existing.setId(101L);
        when(accountService.getOrCreateAccount(AccountOwnerType.PLATFORM, AccountService.PLATFORM_OWNER_ID))
                .thenReturn(account);
        when(ledgerMapper.selectByAccountAndBiz(9L, LedgerBizType.ORDER.name(), 66L)).thenReturn(existing);

        Ledger ledger = ledgerService.record(AccountOwnerType.PLATFORM, AccountService.PLATFORM_OWNER_ID,
                new BigDecimal("199.00"), LedgerBizType.ORDER, 66L, LedgerDirection.IN);

        assertThat(ledger.getId()).isEqualTo(101L);
        verify(accountService, never()).lockAccount(any());
        verify(ledgerMapper, never()).insert(any(Ledger.class));
    }
}
