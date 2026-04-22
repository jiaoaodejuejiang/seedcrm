package com.seedcrm.crm.distributor.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.distributor.dto.DistributorSettlementCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorWithdrawCreateRequest;
import com.seedcrm.crm.distributor.entity.Distributor;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorSettlement;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.distributor.enums.DistributorSettlementStatus;
import com.seedcrm.crm.distributor.enums.DistributorWithdrawStatus;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorSettlementMapper;
import com.seedcrm.crm.distributor.mapper.DistributorWithdrawMapper;
import com.seedcrm.crm.distributor.service.DistributorService;
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
class DistributorSettlementServiceImplTest {

    @Mock
    private DistributorService distributorService;

    @Mock
    private DistributorIncomeDetailMapper distributorIncomeDetailMapper;

    @Mock
    private DistributorSettlementMapper distributorSettlementMapper;

    @Mock
    private DistributorWithdrawMapper distributorWithdrawMapper;

    private DistributorSettlementServiceImpl distributorSettlementService;

    @BeforeEach
    void setUp() {
        distributorSettlementService = new DistributorSettlementServiceImpl(distributorService,
                distributorIncomeDetailMapper, distributorSettlementMapper, distributorWithdrawMapper);
    }

    @Test
    void createSettlementShouldAggregateIncomeAndBindDetails() {
        Distributor distributor = new Distributor();
        distributor.setId(7L);
        when(distributorService.getByIdOrThrow(7L)).thenReturn(distributor);

        DistributorSettlementCreateRequest request = new DistributorSettlementCreateRequest();
        request.setDistributorId(7L);
        request.setStartTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        request.setEndTime(LocalDateTime.of(2026, 4, 30, 23, 59, 59));

        DistributorIncomeDetail detail1 = new DistributorIncomeDetail();
        detail1.setId(1L);
        detail1.setIncomeAmount(new BigDecimal("100.00"));
        DistributorIncomeDetail detail2 = new DistributorIncomeDetail();
        detail2.setId(2L);
        detail2.setIncomeAmount(new BigDecimal("60.00"));
        when(distributorIncomeDetailMapper.selectList(any())).thenReturn(List.of(detail1, detail2));
        when(distributorSettlementMapper.insert(any(DistributorSettlement.class))).thenAnswer(invocation -> {
            DistributorSettlement settlement = invocation.getArgument(0);
            settlement.setId(88L);
            return 1;
        });
        when(distributorIncomeDetailMapper.updateById(any(DistributorIncomeDetail.class))).thenReturn(1);

        DistributorSettlement settlement = distributorSettlementService.createSettlement(request);

        assertThat(settlement.getTotalAmount()).isEqualByComparingTo("160.00");
        assertThat(settlement.getStatus()).isEqualTo(DistributorSettlementStatus.INIT.name());

        ArgumentCaptor<DistributorIncomeDetail> detailCaptor = ArgumentCaptor.forClass(DistributorIncomeDetail.class);
        verify(distributorIncomeDetailMapper, times(2)).updateById(detailCaptor.capture());
        assertThat(detailCaptor.getAllValues()).extracting(DistributorIncomeDetail::getSettlementId)
                .containsOnly(88L);
    }

    @Test
    void createWithdrawShouldUseWithdrawableBalance() {
        Distributor distributor = new Distributor();
        distributor.setId(7L);
        when(distributorService.getByIdOrThrow(7L)).thenReturn(distributor);

        DistributorSettlement settlement = new DistributorSettlement();
        settlement.setTotalAmount(new BigDecimal("300.00"));
        when(distributorSettlementMapper.selectList(any())).thenReturn(List.of(settlement));
        when(distributorWithdrawMapper.selectList(any())).thenReturn(List.of());
        when(distributorWithdrawMapper.insert(any(DistributorWithdraw.class))).thenReturn(1);

        DistributorWithdrawCreateRequest request = new DistributorWithdrawCreateRequest();
        request.setDistributorId(7L);
        request.setAmount(new BigDecimal("120.00"));

        DistributorWithdraw withdraw = distributorSettlementService.createWithdraw(request);

        assertThat(withdraw.getStatus()).isEqualTo(DistributorWithdrawStatus.PENDING.name());
        assertThat(withdraw.getAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void approveWithdrawShouldAllowPendingToPaid() {
        DistributorWithdraw withdraw = new DistributorWithdraw();
        withdraw.setId(6L);
        withdraw.setStatus(DistributorWithdrawStatus.PENDING.name());
        when(distributorWithdrawMapper.selectById(6L)).thenReturn(withdraw);
        when(distributorWithdrawMapper.updateById(any(DistributorWithdraw.class))).thenReturn(1);

        DistributorWithdraw updated = distributorSettlementService.approveWithdraw(6L, DistributorWithdrawStatus.PAID);

        assertThat(updated.getStatus()).isEqualTo(DistributorWithdrawStatus.PAID.name());
    }

    @Test
    void createSettlementShouldRejectWhenNoIncomeDetails() {
        Distributor distributor = new Distributor();
        distributor.setId(7L);
        when(distributorService.getByIdOrThrow(7L)).thenReturn(distributor);

        DistributorSettlementCreateRequest request = new DistributorSettlementCreateRequest();
        request.setDistributorId(7L);
        request.setStartTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        request.setEndTime(LocalDateTime.of(2026, 4, 30, 23, 59, 59));
        when(distributorIncomeDetailMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> distributorSettlementService.createSettlement(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no unsettled distributor income details");
    }
}
