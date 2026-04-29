package com.seedcrm.crm.salary.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.risk.service.IdempotentService;
import com.seedcrm.crm.risk.service.impl.RiskControlServiceImpl;
import com.seedcrm.crm.salary.dto.SalarySettlementPolicyDtos;
import com.seedcrm.crm.salary.dto.WithdrawCreateRequest;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.mapper.SalarySettlementMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import com.seedcrm.crm.salary.service.SalarySettlementPolicyService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceConcurrencyTest {

    @Mock
    private SalarySettlementMapper salarySettlementMapper;

    @Mock
    private WithdrawRecordMapper withdrawRecordMapper;

    @Mock
    private SalarySettlementPolicyService salarySettlementPolicyService;

    @Mock
    private FinanceService financeService;

    @Mock
    private DbLockService dbLockService;

    @Mock
    private IdempotentService idempotentService;

    private WithdrawServiceImpl withdrawService;

    @BeforeEach
    void setUp() {
        withdrawService = new WithdrawServiceImpl(salarySettlementMapper, withdrawRecordMapper, salarySettlementPolicyService,
                financeService, dbLockService, idempotentService, new RiskControlServiceImpl());
    }

    @Test
    void secondWithdrawRequestShouldBeRejectedAfterFirstRequestReservesBalance() {
        SalarySettlement settlement = new SalarySettlement();
        settlement.setTotalAmount(new BigDecimal("100.00"));
        when(salarySettlementMapper.selectList(any())).thenReturn(List.of(settlement));
        when(salarySettlementPolicyService.simulate(any())).thenReturn(auditPolicy());

        List<WithdrawRecord> existingWithdraws = new ArrayList<>();
        AtomicLong idSequence = new AtomicLong(1L);
        when(withdrawRecordMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(existingWithdraws));
        when(withdrawRecordMapper.insert(any(WithdrawRecord.class))).thenAnswer(invocation -> {
            WithdrawRecord record = invocation.getArgument(0);
            record.setId(idSequence.getAndIncrement());
            existingWithdraws.add(record);
            return 1;
        });

        WithdrawCreateRequest request = new WithdrawCreateRequest();
        request.setUserId(9L);
        request.setSubjectType("DISTRIBUTOR");
        request.setAmount(new BigDecimal("80.00"));

        WithdrawRecord first = withdrawService.createWithdraw(request);

        assertThat(first.getId()).isEqualTo(1L);
        assertThatThrownBy(() -> withdrawService.createWithdraw(request))
                .hasMessageContaining("exceeds account balance");
    }

    private SalarySettlementPolicyDtos.SimulateResponse auditPolicy() {
        SalarySettlementPolicyDtos.SimulateResponse response = new SalarySettlementPolicyDtos.SimulateResponse();
        response.setMatched(true);
        response.setSubjectType("DISTRIBUTOR");
        response.setSettlementMode("WITHDRAW_AUDIT");
        response.setRequiresAudit(true);
        return response;
    }
}
