package com.seedcrm.crm.risk.service.impl;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorSettlement;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorSettlementMapper;
import com.seedcrm.crm.distributor.mapper.DistributorWithdrawMapper;
import com.seedcrm.crm.finance.entity.Account;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.service.AccountService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.salary.mapper.SalarySettlementMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DbLockServiceImpl implements DbLockService {

    private final OrderMapper orderMapper;
    private final PlanOrderMapper planOrderMapper;
    private final AccountService accountService;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final DistributorWithdrawMapper distributorWithdrawMapper;
    private final SalarySettlementMapper salarySettlementMapper;
    private final DistributorSettlementMapper distributorSettlementMapper;
    private final SalaryDetailMapper salaryDetailMapper;
    private final DistributorIncomeDetailMapper distributorIncomeDetailMapper;

    public DbLockServiceImpl(OrderMapper orderMapper,
                             PlanOrderMapper planOrderMapper,
                             AccountService accountService,
                             WithdrawRecordMapper withdrawRecordMapper,
                             DistributorWithdrawMapper distributorWithdrawMapper,
                             SalarySettlementMapper salarySettlementMapper,
                             DistributorSettlementMapper distributorSettlementMapper,
                             SalaryDetailMapper salaryDetailMapper,
                             DistributorIncomeDetailMapper distributorIncomeDetailMapper) {
        this.orderMapper = orderMapper;
        this.planOrderMapper = planOrderMapper;
        this.accountService = accountService;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.distributorWithdrawMapper = distributorWithdrawMapper;
        this.salarySettlementMapper = salarySettlementMapper;
        this.distributorSettlementMapper = distributorSettlementMapper;
        this.salaryDetailMapper = salaryDetailMapper;
        this.distributorIncomeDetailMapper = distributorIncomeDetailMapper;
    }

    @Override
    public Order lockOrder(Long orderId) {
        Order order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        return order;
    }

    @Override
    public PlanOrder lockPlanOrder(Long planOrderId) {
        PlanOrder planOrder = planOrderMapper.selectByIdForUpdate(planOrderId);
        if (planOrder == null) {
            throw new BusinessException("plan order not found");
        }
        return planOrder;
    }

    @Override
    public PlanOrder lockPlanOrderByOrderId(Long orderId) {
        PlanOrder planOrder = planOrderMapper.selectByOrderIdForUpdate(orderId);
        if (planOrder == null) {
            throw new BusinessException("plan order must exist before settlement");
        }
        return planOrder;
    }

    @Override
    public Account lockAccount(AccountOwnerType ownerType, Long ownerId) {
        Account account = accountService.getOrCreateAccount(ownerType, ownerId);
        return accountService.lockAccount(account.getId());
    }

    @Override
    public WithdrawRecord lockWithdrawRecord(Long withdrawId) {
        WithdrawRecord withdrawRecord = withdrawRecordMapper.selectByIdForUpdate(withdrawId);
        if (withdrawRecord == null) {
            throw new BusinessException("withdraw record not found");
        }
        return withdrawRecord;
    }

    @Override
    public DistributorWithdraw lockDistributorWithdraw(Long withdrawId) {
        DistributorWithdraw withdrawRecord = distributorWithdrawMapper.selectByIdForUpdate(withdrawId);
        if (withdrawRecord == null) {
            throw new BusinessException("distributor withdraw record not found");
        }
        return withdrawRecord;
    }

    @Override
    public SalarySettlement lockSalarySettlement(Long settlementId) {
        SalarySettlement settlement = salarySettlementMapper.selectByIdForUpdate(settlementId);
        if (settlement == null) {
            throw new BusinessException("salary settlement not found");
        }
        return settlement;
    }

    @Override
    public DistributorSettlement lockDistributorSettlement(Long settlementId) {
        DistributorSettlement settlement = distributorSettlementMapper.selectByIdForUpdate(settlementId);
        if (settlement == null) {
            throw new BusinessException("distributor settlement not found");
        }
        return settlement;
    }

    @Override
    public List<SalaryDetail> lockUnsettledSalaryDetails(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        return salaryDetailMapper.selectUnsettledForSettlement(userId, startTime, endTime);
    }

    @Override
    public List<DistributorIncomeDetail> lockUnsettledDistributorIncomeDetails(Long distributorId,
                                                                               LocalDateTime startTime,
                                                                               LocalDateTime endTime) {
        return distributorIncomeDetailMapper.selectUnsettledForSettlement(distributorId, startTime, endTime);
    }
}
