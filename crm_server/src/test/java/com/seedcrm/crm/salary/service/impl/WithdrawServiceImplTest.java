package com.seedcrm.crm.salary.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.salary.dto.WithdrawCreateRequest;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.enums.WithdrawStatus;
import com.seedcrm.crm.salary.mapper.SalarySettlementMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
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

    private WithdrawServiceImpl withdrawService;

    @BeforeEach
    void setUp() {
        withdrawService = new WithdrawServiceImpl(salarySettlementMapper, withdrawRecordMapper);
    }

    @Test
    void createWithdrawShouldUseAvailableBalance() {
        SalarySettlement settlement = new SalarySettlement();
        settlement.setTotalAmount(new BigDecimal("300.00"));
        when(salarySettlementMapper.selectList(any())).thenReturn(List.of(settlement));
        when(withdrawRecordMapper.selectList(any())).thenReturn(List.of());
        when(withdrawRecordMapper.insert(any(WithdrawRecord.class))).thenReturn(1);

        WithdrawCreateRequest request = new WithdrawCreateRequest();
        request.setUserId(9L);
        request.setAmount(new BigDecimal("120.00"));

        WithdrawRecord record = withdrawService.createWithdraw(request);

        assertThat(record.getStatus()).isEqualTo(WithdrawStatus.PENDING.name());
        assertThat(record.getAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void approveWithdrawShouldAllowPendingToPaid() {
        WithdrawRecord record = new WithdrawRecord();
        record.setId(3L);
        record.setStatus(WithdrawStatus.PENDING.name());
        when(withdrawRecordMapper.selectById(3L)).thenReturn(record);
        when(withdrawRecordMapper.updateById(any(WithdrawRecord.class))).thenReturn(1);

        WithdrawRecord updated = withdrawService.approveWithdraw(3L, WithdrawStatus.PAID);

        assertThat(updated.getStatus()).isEqualTo(WithdrawStatus.PAID.name());
    }

    @Test
    void createWithdrawShouldRejectExcessAmount() {
        SalarySettlement settlement = new SalarySettlement();
        settlement.setTotalAmount(new BigDecimal("100.00"));
        when(salarySettlementMapper.selectList(any())).thenReturn(List.of(settlement));
        when(withdrawRecordMapper.selectList(any())).thenReturn(List.of());

        WithdrawCreateRequest request = new WithdrawCreateRequest();
        request.setUserId(9L);
        request.setAmount(new BigDecimal("120.00"));

        assertThatThrownBy(() -> withdrawService.createWithdraw(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds withdrawable");
    }
}
