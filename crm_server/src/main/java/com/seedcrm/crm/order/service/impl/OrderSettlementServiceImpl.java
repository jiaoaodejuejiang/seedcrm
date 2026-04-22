package com.seedcrm.crm.order.service.impl;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.service.CustomerTagService;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.service.DistributorIncomeService;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.service.OrderSettlementService;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.risk.enums.IdempotentBizType;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.risk.service.IdempotentService;
import com.seedcrm.crm.risk.service.RiskControlService;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.service.SalaryService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSettlementServiceImpl implements OrderSettlementService {

    private final DbLockService dbLockService;
    private final IdempotentService idempotentService;
    private final RiskControlService riskControlService;
    private final SalaryService salaryService;
    private final DistributorIncomeService distributorIncomeService;
    private final FinanceService financeService;
    private final CustomerTagService customerTagService;

    public OrderSettlementServiceImpl(DbLockService dbLockService,
                                      IdempotentService idempotentService,
                                      RiskControlService riskControlService,
                                      SalaryService salaryService,
                                      DistributorIncomeService distributorIncomeService,
                                      FinanceService financeService,
                                      CustomerTagService customerTagService) {
        this.dbLockService = dbLockService;
        this.idempotentService = idempotentService;
        this.riskControlService = riskControlService;
        this.salaryService = salaryService;
        this.distributorIncomeService = distributorIncomeService;
        this.financeService = financeService;
        this.customerTagService = customerTagService;
    }

    @Override
    @Transactional
    public Order settleCompletedOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException("orderId is required");
        }
        Order order = dbLockService.lockOrder(orderId);
        if (!OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            throw new BusinessException("order must be completed before settlement");
        }

        PlanOrder planOrder = dbLockService.lockPlanOrderByOrderId(orderId);
        if (!PlanOrderStatus.FINISHED.name().equals(planOrder.getStatus())) {
            throw new BusinessException("plan order must be finished before order settlement");
        }

        String bizKey = "ORDER_" + orderId;
        if (!idempotentService.tryStart(bizKey, IdempotentBizType.ORDER)) {
            return order;
        }

        try {
            List<SalaryDetail> salaryDetails = salaryService.calculateForPlanOrder(planOrder.getId());
            DistributorIncomeDetail distributorIncomeDetail = distributorIncomeService.calculate(orderId);
            riskControlService.validateSplitTotalNotExceedOrderAmount(order.getAmount(),
                    sumSalaryAmount(salaryDetails),
                    distributorIncomeDetail == null ? BigDecimal.ZERO : distributorIncomeDetail.getIncomeAmount());
            financeService.recordOrderIncome(order);
            if (order.getCustomerId() != null) {
                customerTagService.updateTag(order.getCustomerId());
            }
            idempotentService.markSuccess(bizKey);
            return order;
        } catch (RuntimeException exception) {
            idempotentService.markFail(bizKey);
            throw exception;
        }
    }

    private BigDecimal sumSalaryAmount(List<SalaryDetail> salaryDetails) {
        if (salaryDetails == null) {
            return BigDecimal.ZERO;
        }
        return salaryDetails.stream()
                .map(SalaryDetail::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
