package com.seedcrm.crm.distributor.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.distributor.entity.Distributor;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorRule;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorRuleMapper;
import com.seedcrm.crm.distributor.service.DistributorService;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.risk.service.IdempotentService;
import com.seedcrm.crm.risk.service.RiskControlService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributorIncomeServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private DistributorRuleMapper distributorRuleMapper;

    @Mock
    private DistributorIncomeDetailMapper distributorIncomeDetailMapper;

    @Mock
    private DistributorService distributorService;

    @Mock
    private FinanceService financeService;

    @Mock
    private DbLockService dbLockService;

    @Mock
    private IdempotentService idempotentService;

    @Mock
    private RiskControlService riskControlService;

    private DistributorIncomeServiceImpl distributorIncomeService;

    @BeforeEach
    void setUp() {
        distributorIncomeService = new DistributorIncomeServiceImpl(orderMapper, distributorRuleMapper,
                distributorIncomeDetailMapper, distributorService, financeService,
                dbLockService, idempotentService, riskControlService);
    }

    @Test
    void calculateShouldCreateIncomeDetailForDistributorOrder() {
        Order order = new Order();
        order.setId(11L);
        order.setSourceChannel(SourceChannel.DISTRIBUTOR.name());
        order.setSourceId(8L);
        order.setStatus(OrderStatus.COMPLETED.name());
        order.setAmount(new BigDecimal("1000.00"));
        when(dbLockService.lockOrder(11L)).thenReturn(order);
        when(distributorIncomeDetailMapper.selectByOrderAndDistributor(11L, 8L)).thenReturn(null);
        when(idempotentService.tryStart("DIST_11", com.seedcrm.crm.risk.enums.IdempotentBizType.DISTRIBUTOR))
                .thenReturn(true);

        Distributor distributor = new Distributor();
        distributor.setId(8L);
        when(distributorService.getByIdOrThrow(8L)).thenReturn(distributor);

        DistributorRule rule = new DistributorRule();
        rule.setDistributorId(8L);
        rule.setRuleType("PERCENT");
        rule.setRuleValue(new BigDecimal("0.10"));
        when(distributorRuleMapper.selectOne(any())).thenReturn(rule);
        when(distributorIncomeDetailMapper.insert(any(DistributorIncomeDetail.class))).thenAnswer(invocation -> {
            DistributorIncomeDetail detail = invocation.getArgument(0);
            detail.setId(99L);
            return 1;
        });

        DistributorIncomeDetail detail = distributorIncomeService.calculate(11L);

        assertThat(detail.getDistributorId()).isEqualTo(8L);
        assertThat(detail.getOrderId()).isEqualTo(11L);
        assertThat(detail.getIncomeAmount()).isEqualByComparingTo("100.00");
        verify(financeService).recordDistributorIncome(detail);
        verify(idempotentService).markSuccess("DIST_11");
    }

    @Test
    void calculateShouldReturnExistingDetailWhenAlreadyCalculated() {
        Order order = new Order();
        order.setId(12L);
        order.setSourceChannel(SourceChannel.DISTRIBUTOR.name());
        order.setSourceId(8L);
        order.setStatus(OrderStatus.COMPLETED.name());
        order.setAmount(new BigDecimal("1000.00"));
        when(dbLockService.lockOrder(12L)).thenReturn(order);

        DistributorIncomeDetail existing = new DistributorIncomeDetail();
        existing.setId(101L);
        existing.setOrderId(12L);
        existing.setIncomeAmount(new BigDecimal("88.00"));
        when(distributorIncomeDetailMapper.selectByOrderAndDistributor(12L, 8L)).thenReturn(existing);
        when(idempotentService.tryStart("DIST_12", com.seedcrm.crm.risk.enums.IdempotentBizType.DISTRIBUTOR))
                .thenReturn(false);

        DistributorIncomeDetail detail = distributorIncomeService.calculate(12L);

        assertThat(detail.getId()).isEqualTo(101L);
        verify(financeService, never()).recordDistributorIncome(existing);
        verify(distributorIncomeDetailMapper, never()).insert(any(DistributorIncomeDetail.class));
    }
}
