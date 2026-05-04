package com.seedcrm.crm.finance.service;

import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.finance.dto.FinanceBalanceResponse;
import com.seedcrm.crm.finance.dto.FinanceCheckResponse;
import com.seedcrm.crm.finance.dto.FinanceRefundRecordListResponse;
import com.seedcrm.crm.finance.entity.Ledger;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.enums.LedgerBizType;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.WithdrawRecord;

public interface FinanceService {

    Ledger recordOrderIncome(Order order);

    Ledger recordSalaryIncome(SalaryDetail salaryDetail);

    Ledger recordDistributorIncome(DistributorIncomeDetail incomeDetail);

    Ledger recordUserWithdraw(WithdrawRecord withdrawRecord);

    Ledger recordDistributorWithdraw(DistributorWithdraw withdrawRecord);

    FinanceBalanceResponse getBalance(AccountOwnerType ownerType, Long ownerId);

    FinanceCheckResponse check();

    FinanceRefundRecordListResponse listRefundRecords(String refundScene,
                                                      Long orderId,
                                                      String status,
                                                      String orderNo,
                                                      Integer page,
                                                      Integer pageSize);

    boolean hasLedgerRecord(LedgerBizType bizType, Long bizId);
}
