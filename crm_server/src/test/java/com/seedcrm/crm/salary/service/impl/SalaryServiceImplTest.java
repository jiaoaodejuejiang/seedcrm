package com.seedcrm.crm.salary.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.OrderRoleRecordMapper;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.salary.dto.SalaryBalanceResponse;
import com.seedcrm.crm.salary.dto.SalaryStatResponse;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.SalaryRule;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.salary.mapper.SalaryRuleMapper;
import com.seedcrm.crm.salary.mapper.SalarySettlementMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.risk.service.IdempotentService;
import com.seedcrm.crm.risk.service.RiskControlService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalaryServiceImplTest {

    @Mock
    private OrderRoleRecordMapper orderRoleRecordMapper;

    @Mock
    private PlanOrderMapper planOrderMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private SalaryRuleMapper salaryRuleMapper;

    @Mock
    private SalaryDetailMapper salaryDetailMapper;

    @Mock
    private SalarySettlementMapper salarySettlementMapper;

    @Mock
    private WithdrawRecordMapper withdrawRecordMapper;

    @Mock
    private FinanceService financeService;

    @Mock
    private DbLockService dbLockService;

    @Mock
    private IdempotentService idempotentService;

    @Mock
    private RiskControlService riskControlService;

    private SalaryServiceImpl salaryService;

    @BeforeEach
    void setUp() {
        salaryService = new SalaryServiceImpl(orderRoleRecordMapper, planOrderMapper, orderMapper, salaryRuleMapper,
                salaryDetailMapper, salarySettlementMapper, withdrawRecordMapper, financeService,
                dbLockService, idempotentService, riskControlService);
    }

    @Test
    void statShouldAggregateOrderRoleAndServiceCounts() {
        when(orderRoleRecordMapper.countDistinctPlanOrdersByUserId(9L)).thenReturn(3L);
        when(orderRoleRecordMapper.countFinishedServicesByUserId(9L)).thenReturn(2L);
        when(orderRoleRecordMapper.selectRoleDistributionByUserId(9L)).thenReturn(List.of(
                Map.of("roleCode", "CONSULTANT", "roleCount", 2L),
                Map.of("roleCode", "DOCTOR", "roleCount", 1L)
        ));

        SalaryStatResponse response = salaryService.stat(9L);

        assertThat(response.getParticipateOrderCount()).isEqualTo(3L);
        assertThat(response.getServiceCount()).isEqualTo(2L);
        assertThat(response.getRoleDistribution()).containsEntry("CONSULTANT", 2L);
        assertThat(response.getRoleDistribution()).containsEntry("DOCTOR", 1L);
    }

    @Test
    void calculateForPlanOrderShouldCreateSalaryDetailsFromRules() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(11L);
        planOrder.setOrderId(88L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        when(dbLockService.lockPlanOrder(11L)).thenReturn(planOrder);
        when(idempotentService.tryStart("SALARY_11", com.seedcrm.crm.risk.enums.IdempotentBizType.SALARY))
                .thenReturn(true);

        when(salaryDetailMapper.selectList(any())).thenReturn(List.of(), List.of(buildDetail(11L, 7L, "CONSULTANT", "100.00"),
                buildDetail(11L, 8L, "DOCTOR", "200.00")));

        Order order = new Order();
        order.setId(88L);
        order.setAmount(new BigDecimal("1000.00"));
        when(orderMapper.selectById(88L)).thenReturn(order);

        OrderRoleRecord consultant = new OrderRoleRecord();
        consultant.setPlanOrderId(11L);
        consultant.setUserId(7L);
        consultant.setRoleCode("CONSULTANT");
        consultant.setIsCurrent(1);
        consultant.setCreateTime(LocalDateTime.now().minusMinutes(2));

        OrderRoleRecord doctor = new OrderRoleRecord();
        doctor.setPlanOrderId(11L);
        doctor.setUserId(8L);
        doctor.setRoleCode("DOCTOR");
        doctor.setIsCurrent(1);
        doctor.setCreateTime(LocalDateTime.now().minusMinutes(1));
        when(orderRoleRecordMapper.selectList(any())).thenReturn(List.of(consultant, doctor));

        SalaryRule consultantRule = new SalaryRule();
        consultantRule.setRoleCode("CONSULTANT");
        consultantRule.setRuleType("PERCENT");
        consultantRule.setRuleValue(new BigDecimal("0.1000"));

        SalaryRule doctorRule = new SalaryRule();
        doctorRule.setRoleCode("DOCTOR");
        doctorRule.setRuleType("FIXED");
        doctorRule.setRuleValue(new BigDecimal("200.00"));
        when(salaryRuleMapper.selectOne(any())).thenReturn(consultantRule, doctorRule);
        when(salaryDetailMapper.insert(any(SalaryDetail.class))).thenReturn(1);

        List<SalaryDetail> response = salaryService.calculateForPlanOrder(11L);

        assertThat(response).hasSize(2);
        ArgumentCaptor<SalaryDetail> captor = ArgumentCaptor.forClass(SalaryDetail.class);
        verify(salaryDetailMapper, times(2)).insert(captor.capture());
        verify(financeService, times(2)).recordSalaryIncome(any(SalaryDetail.class));
        assertThat(captor.getAllValues()).extracting(SalaryDetail::getAmount)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("200.00"));
        verify(idempotentService).markSuccess("SALARY_11");
    }

    @Test
    void recalculateShouldRejectSettledSalaryDetails() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(12L);
        planOrder.setOrderId(66L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        when(planOrderMapper.selectById(12L)).thenReturn(planOrder);

        SalaryDetail settledDetail = new SalaryDetail();
        settledDetail.setId(3L);
        settledDetail.setSettlementId(99L);
        when(salaryDetailMapper.selectList(any())).thenReturn(List.of(settledDetail));

        assertThatThrownBy(() -> salaryService.recalculateForPlanOrder(12L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("after settlement");
    }

    @Test
    void recalculateShouldRejectWhenLedgerAlreadyPosted() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(12L);
        planOrder.setOrderId(66L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        when(planOrderMapper.selectById(12L)).thenReturn(planOrder);

        SalaryDetail existingDetail = new SalaryDetail();
        existingDetail.setId(3L);
        when(salaryDetailMapper.selectList(any())).thenReturn(List.of(existingDetail));
        when(financeService.hasLedgerRecord(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> salaryService.recalculateForPlanOrder(12L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("after ledger posting");
    }

    @Test
    void balanceShouldAggregateSettlementAndWithdrawAmounts() {
        SalaryDetail unsettledDetail = new SalaryDetail();
        unsettledDetail.setAmount(new BigDecimal("80.00"));
        when(salaryDetailMapper.selectList(any())).thenReturn(List.of(unsettledDetail));

        SalarySettlement settlement = new SalarySettlement();
        settlement.setTotalAmount(new BigDecimal("300.00"));
        when(salarySettlementMapper.selectList(any())).thenReturn(List.of(settlement));

        WithdrawRecord withdrawRecord = new WithdrawRecord();
        withdrawRecord.setAmount(new BigDecimal("120.00"));
        when(withdrawRecordMapper.selectList(any())).thenReturn(List.of(withdrawRecord));

        SalaryBalanceResponse response = salaryService.balance(5L);

        assertThat(response.getUnsettledAmount()).isEqualByComparingTo("80.00");
        assertThat(response.getSettledAmount()).isEqualByComparingTo("300.00");
        assertThat(response.getWithdrawnAmount()).isEqualByComparingTo("120.00");
        assertThat(response.getWithdrawableAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    void statShouldRejectInvalidUserId() {
        assertThatThrownBy(() -> salaryService.stat(0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("userId is required");
    }

    private SalaryDetail buildDetail(Long planOrderId, Long userId, String roleCode, String amount) {
        SalaryDetail detail = new SalaryDetail();
        detail.setPlanOrderId(planOrderId);
        detail.setUserId(userId);
        detail.setRoleCode(roleCode);
        detail.setAmount(new BigDecimal(amount));
        return detail;
    }
}
