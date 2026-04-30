package com.seedcrm.crm.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.customer.service.CustomerTagService;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.service.DistributorIncomeService;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.risk.service.IdempotentService;
import com.seedcrm.crm.risk.service.RiskControlService;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.service.SalaryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSettlementServiceImplTest {

    @Mock
    private DbLockService dbLockService;

    @Mock
    private IdempotentService idempotentService;

    @Mock
    private RiskControlService riskControlService;

    @Mock
    private SalaryService salaryService;

    @Mock
    private DistributorIncomeService distributorIncomeService;

    @Mock
    private FinanceService financeService;

    @Mock
    private CustomerTagService customerTagService;

    private OrderSettlementServiceImpl orderSettlementService;

    @BeforeEach
    void setUp() {
        orderSettlementService = new OrderSettlementServiceImpl(dbLockService, idempotentService, riskControlService,
                salaryService, distributorIncomeService, financeService, customerTagService);
    }

    @Test
    void settleCompletedOrderShouldRunExactlyOnce() {
        Order order = new Order();
        order.setId(30L);
        order.setCustomerId(9L);
        order.setAmount(new BigDecimal("1000.00"));
        order.setStatus(OrderStatus.COMPLETED.name());
        when(dbLockService.lockOrder(30L)).thenReturn(order);

        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(11L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        when(dbLockService.lockPlanOrderByOrderId(30L)).thenReturn(planOrder);
        when(idempotentService.tryStart("ORDER_30", com.seedcrm.crm.risk.enums.IdempotentBizType.ORDER))
                .thenReturn(true);

        SalaryDetail salaryDetail = new SalaryDetail();
        salaryDetail.setAmount(new BigDecimal("200.00"));
        when(salaryService.calculateForPlanOrder(11L)).thenReturn(List.of(salaryDetail));

        DistributorIncomeDetail incomeDetail = new DistributorIncomeDetail();
        incomeDetail.setIncomeAmount(new BigDecimal("100.00"));
        when(distributorIncomeService.calculate(30L)).thenReturn(incomeDetail);

        Order settledOrder = orderSettlementService.settleCompletedOrder(30L);

        assertThat(settledOrder.getId()).isEqualTo(30L);
        verify(financeService).recordOrderIncome(order);
        verify(customerTagService).updateTag(9L);
        verify(idempotentService).markSuccess("ORDER_30");
    }

    @Test
    void settleExternalDistributionOrderShouldSkipInternalDistributorIncome() {
        Order order = new Order();
        order.setId(32L);
        order.setCustomerId(9L);
        order.setSource("distribution");
        order.setExternalPartnerCode("DISTRIBUTION");
        order.setExternalOrderId("dist_order_001");
        order.setAmount(new BigDecimal("1000.00"));
        order.setStatus(OrderStatus.COMPLETED.name());
        when(dbLockService.lockOrder(32L)).thenReturn(order);

        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(13L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        when(dbLockService.lockPlanOrderByOrderId(32L)).thenReturn(planOrder);
        when(idempotentService.tryStart("ORDER_32", com.seedcrm.crm.risk.enums.IdempotentBizType.ORDER))
                .thenReturn(true);

        SalaryDetail salaryDetail = new SalaryDetail();
        salaryDetail.setAmount(new BigDecimal("200.00"));
        when(salaryService.calculateForPlanOrder(13L)).thenReturn(List.of(salaryDetail));

        Order settledOrder = orderSettlementService.settleCompletedOrder(32L);

        assertThat(settledOrder.getId()).isEqualTo(32L);
        verify(distributorIncomeService, never()).calculate(32L);
        verify(riskControlService).validateSplitTotalNotExceedOrderAmount(
                order.getAmount(), new BigDecimal("200.00"), BigDecimal.ZERO);
        verify(financeService).recordOrderIncome(order);
        verify(customerTagService).updateTag(9L);
        verify(idempotentService).markSuccess("ORDER_32");
    }

    @Test
    void settleCompletedOrderShouldReturnWhenDuplicateSubmissionArrives() {
        Order order = new Order();
        order.setId(31L);
        order.setAmount(new BigDecimal("1000.00"));
        order.setStatus(OrderStatus.COMPLETED.name());
        when(dbLockService.lockOrder(31L)).thenReturn(order);

        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(12L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        when(dbLockService.lockPlanOrderByOrderId(31L)).thenReturn(planOrder);
        when(idempotentService.tryStart("ORDER_31", com.seedcrm.crm.risk.enums.IdempotentBizType.ORDER))
                .thenReturn(false);

        Order settledOrder = orderSettlementService.settleCompletedOrder(31L);

        assertThat(settledOrder.getId()).isEqualTo(31L);
        verify(financeService, never()).recordOrderIncome(order);
        verify(customerTagService, never()).updateTag(org.mockito.ArgumentMatchers.anyLong());
    }
}
