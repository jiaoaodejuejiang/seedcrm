package com.seedcrm.crm.salary.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.salary.dto.SalarySettlementCreateRequest;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.enums.SalarySettlementStatus;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.salary.mapper.SalarySettlementMapper;
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
class SettlementServiceImplTest {

    @Mock
    private SalaryDetailMapper salaryDetailMapper;

    @Mock
    private SalarySettlementMapper salarySettlementMapper;

    private SettlementServiceImpl settlementService;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementServiceImpl(salaryDetailMapper, salarySettlementMapper);
    }

    @Test
    void createSettlementShouldAggregateDetailsAndBindSettlement() {
        SalarySettlementCreateRequest request = new SalarySettlementCreateRequest();
        request.setUserId(7L);
        request.setStartTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        request.setEndTime(LocalDateTime.of(2026, 4, 30, 23, 59, 59));

        SalaryDetail detail1 = new SalaryDetail();
        detail1.setId(1L);
        detail1.setAmount(new BigDecimal("100.00"));
        SalaryDetail detail2 = new SalaryDetail();
        detail2.setId(2L);
        detail2.setAmount(new BigDecimal("80.00"));
        when(salaryDetailMapper.selectList(any())).thenReturn(List.of(detail1, detail2));
        when(salarySettlementMapper.insert(any(SalarySettlement.class))).thenAnswer(invocation -> {
            SalarySettlement settlement = invocation.getArgument(0);
            settlement.setId(88L);
            return 1;
        });
        when(salaryDetailMapper.updateById(any(SalaryDetail.class))).thenReturn(1);

        SalarySettlement settlement = settlementService.createSettlement(request);

        assertThat(settlement.getTotalAmount()).isEqualByComparingTo("180.00");
        assertThat(settlement.getStatus()).isEqualTo(SalarySettlementStatus.INIT.name());

        ArgumentCaptor<SalaryDetail> detailCaptor = ArgumentCaptor.forClass(SalaryDetail.class);
        verify(salaryDetailMapper, times(2)).updateById(detailCaptor.capture());
        assertThat(detailCaptor.getAllValues()).extracting(SalaryDetail::getSettlementId)
                .containsOnly(88L);
    }

    @Test
    void updateStatusShouldAllowInitToConfirmed() {
        SalarySettlement settlement = new SalarySettlement();
        settlement.setId(6L);
        settlement.setStatus(SalarySettlementStatus.INIT.name());
        when(salarySettlementMapper.selectById(6L)).thenReturn(settlement);
        when(salarySettlementMapper.updateById(any(SalarySettlement.class))).thenReturn(1);

        SalarySettlement updated = settlementService.updateStatus(6L, SalarySettlementStatus.CONFIRMED);

        assertThat(updated.getStatus()).isEqualTo(SalarySettlementStatus.CONFIRMED.name());
    }

    @Test
    void createSettlementShouldRejectEmptyDetailSet() {
        SalarySettlementCreateRequest request = new SalarySettlementCreateRequest();
        request.setUserId(7L);
        request.setStartTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        request.setEndTime(LocalDateTime.of(2026, 4, 30, 23, 59, 59));
        when(salaryDetailMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> settlementService.createSettlement(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no unsettled salary details");
    }
}
