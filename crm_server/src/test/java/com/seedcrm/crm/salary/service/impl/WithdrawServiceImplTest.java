package com.seedcrm.crm.salary.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.finance.service.FinanceService;
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
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceImplTest {

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

    @Mock
    private RiskControlService riskControlService;

    private WithdrawServiceImpl withdrawService;

    @BeforeEach
    void setUp() {
        withdrawService = new WithdrawServiceImpl(salarySettlementMapper, withdrawRecordMapper, salarySettlementPolicyService,
                financeService, dbLockService, idempotentService, riskControlService);
    }

    @Test
    void createWithdrawShouldCreatePendingRecordWhenPolicyRequiresAudit() {
        SalarySettlement settlement = new SalarySettlement();
        settlement.setTotalAmount(new BigDecimal("300.00"));
        when(salarySettlementMapper.selectList(any())).thenReturn(List.of(settlement));
        when(withdrawRecordMapper.selectList(any())).thenReturn(List.of());
        when(withdrawRecordMapper.insert(any(WithdrawRecord.class))).thenReturn(1);
        when(salarySettlementPolicyService.simulate(any())).thenReturn(policy("DISTRIBUTOR", "WITHDRAW_AUDIT"));

        WithdrawCreateRequest request = new WithdrawCreateRequest();
        request.setUserId(9L);
        request.setSubjectType("DISTRIBUTOR");
        request.setAmount(new BigDecimal("120.00"));

        WithdrawRecord record = withdrawService.createWithdraw(request);

        assertThat(record.getStatus()).isEqualTo(WithdrawStatus.PENDING.name());
        assertThat(record.getAmount()).isEqualByComparingTo("120.00");
        assertThat(record.getSubjectType()).isEqualTo("DISTRIBUTOR");
        assertThat(record.getSettlementMode()).isEqualTo("WITHDRAW_AUDIT");
        assertThat(record.getAuditRequired()).isEqualTo(1);
    }

    @Test
    void createWithdrawShouldAutoPayWhenPolicyAllowsDirectWithdraw() {
        SalarySettlement settlement = new SalarySettlement();
        settlement.setTotalAmount(new BigDecimal("300.00"));
        when(salarySettlementMapper.selectList(any())).thenReturn(List.of(settlement));
        when(withdrawRecordMapper.selectList(any())).thenReturn(List.of());
        when(withdrawRecordMapper.insert(any(WithdrawRecord.class))).thenReturn(1);
        when(salarySettlementPolicyService.simulate(any())).thenReturn(policy("DISTRIBUTOR", "WITHDRAW_DIRECT"));

        WithdrawCreateRequest request = new WithdrawCreateRequest();
        request.setUserId(9L);
        request.setSubjectType("DISTRIBUTOR");
        request.setAmount(new BigDecimal("120.00"));

        WithdrawRecord record = withdrawService.createWithdraw(request);

        assertThat(record.getStatus()).isEqualTo(WithdrawStatus.PAID.name());
        assertThat(record.getAuditRequired()).isZero();
        assertThat(record.getPaidTime()).isNotNull();
        verify(financeService).recordUserWithdraw(record);
    }

    @Test
    void createWithdrawShouldRejectLedgerOnlyPolicy() {
        when(salarySettlementPolicyService.simulate(any())).thenReturn(policy("INTERNAL_STAFF", "LEDGER_ONLY"));

        WithdrawCreateRequest request = new WithdrawCreateRequest();
        request.setUserId(9L);
        request.setRoleCode("STORE_SERVICE");
        request.setAmount(new BigDecimal("120.00"));

        assertThatThrownBy(() -> withdrawService.createWithdraw(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只记账");
    }

    @Test
    void approveWithdrawShouldAllowPendingToPaid() {
        WithdrawRecord record = new WithdrawRecord();
        record.setId(3L);
        record.setUserId(9L);
        record.setStatus(WithdrawStatus.PENDING.name());
        when(dbLockService.lockWithdrawRecord(3L)).thenReturn(record);
        when(idempotentService.tryStart("WITHDRAW_USER_3", com.seedcrm.crm.risk.enums.IdempotentBizType.WITHDRAW))
                .thenReturn(true);
        when(withdrawRecordMapper.updateById(any(WithdrawRecord.class))).thenReturn(1);

        WithdrawRecord updated = withdrawService.approveWithdraw(3L, WithdrawStatus.PAID, "审核通过");

        assertThat(updated.getStatus()).isEqualTo(WithdrawStatus.PAID.name());
        verify(financeService).recordUserWithdraw(record);
        verify(idempotentService).markSuccess("WITHDRAW_USER_3");
    }

    @Test
    void approveWithdrawShouldAllowPendingToRejectedAndReleaseWithdrawableAmount() {
        WithdrawRecord record = new WithdrawRecord();
        record.setId(5L);
        record.setUserId(9L);
        record.setStatus(WithdrawStatus.PENDING.name());
        when(dbLockService.lockWithdrawRecord(5L)).thenReturn(record);
        when(withdrawRecordMapper.updateById(any(WithdrawRecord.class))).thenReturn(1);

        WithdrawRecord updated = withdrawService.approveWithdraw(5L, WithdrawStatus.REJECTED, "资料不完整");

        assertThat(updated.getStatus()).isEqualTo(WithdrawStatus.REJECTED.name());
        assertThat(updated.getAuditRemark()).isEqualTo("资料不完整");
        verify(financeService, never()).recordUserWithdraw(record);
    }

    @Test
    void approveWithdrawShouldRejectRejectedStatusWithoutRemark() {
        assertThatThrownBy(() -> withdrawService.approveWithdraw(5L, WithdrawStatus.REJECTED, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核说明");
    }

    @Test
    void withdrawableShouldIgnoreRejectedWithdraws() {
        SalarySettlement settlement = new SalarySettlement();
        settlement.setTotalAmount(new BigDecimal("500.00"));
        WithdrawRecord paid = new WithdrawRecord();
        paid.setAmount(new BigDecimal("100.00"));
        paid.setStatus(WithdrawStatus.PAID.name());
        WithdrawRecord rejected = new WithdrawRecord();
        rejected.setAmount(new BigDecimal("200.00"));
        rejected.setStatus(WithdrawStatus.REJECTED.name());
        when(salarySettlementMapper.selectList(any())).thenReturn(List.of(settlement));
        when(withdrawRecordMapper.selectList(any())).thenReturn(List.of(paid, rejected));

        BigDecimal amount = withdrawService.getWithdrawableAmount(9L);

        assertThat(amount).isEqualByComparingTo("400.00");
    }

    @Test
    void createWithdrawShouldRejectExcessAmount() {
        SalarySettlement settlement = new SalarySettlement();
        settlement.setTotalAmount(new BigDecimal("100.00"));
        when(salarySettlementPolicyService.simulate(any())).thenReturn(policy("DISTRIBUTOR", "WITHDRAW_AUDIT"));
        when(salarySettlementMapper.selectList(any())).thenReturn(List.of(settlement));
        when(withdrawRecordMapper.selectList(any())).thenReturn(List.of());

        WithdrawCreateRequest request = new WithdrawCreateRequest();
        request.setUserId(9L);
        request.setSubjectType("DISTRIBUTOR");
        request.setAmount(new BigDecimal("120.00"));
        doThrow(new BusinessException("withdraw amount exceeds withdrawable balance"))
                .when(riskControlService)
                .validateWithdrawAmountNotExceedBalance(eq(new BigDecimal("120.00")), eq(new BigDecimal("100.00")));

        assertThatThrownBy(() -> withdrawService.createWithdraw(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds withdrawable");
    }

    private SalarySettlementPolicyDtos.SimulateResponse policy(String subjectType, String settlementMode) {
        SalarySettlementPolicyDtos.SimulateResponse response = new SalarySettlementPolicyDtos.SimulateResponse();
        response.setMatched(true);
        response.setSubjectType(subjectType);
        response.setSettlementMode(settlementMode);
        response.setLedgerOnly("LEDGER_ONLY".equals(settlementMode));
        response.setAutoApprove("WITHDRAW_DIRECT".equals(settlementMode));
        response.setRequiresAudit("WITHDRAW_AUDIT".equals(settlementMode));
        return response;
    }
}
