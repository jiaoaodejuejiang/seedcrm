package com.seedcrm.crm.risk.service;

import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorSettlement;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.finance.entity.Account;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import java.time.LocalDateTime;
import java.util.List;

public interface DbLockService {

    Order lockOrder(Long orderId);

    PlanOrder lockPlanOrder(Long planOrderId);

    PlanOrder lockPlanOrderByOrderId(Long orderId);

    Account lockAccount(AccountOwnerType ownerType, Long ownerId);

    WithdrawRecord lockWithdrawRecord(Long withdrawId);

    DistributorWithdraw lockDistributorWithdraw(Long withdrawId);

    SalarySettlement lockSalarySettlement(Long settlementId);

    DistributorSettlement lockDistributorSettlement(Long settlementId);

    List<SalaryDetail> lockUnsettledSalaryDetails(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    List<DistributorIncomeDetail> lockUnsettledDistributorIncomeDetails(Long distributorId,
                                                                        LocalDateTime startTime,
                                                                        LocalDateTime endTime);
}
