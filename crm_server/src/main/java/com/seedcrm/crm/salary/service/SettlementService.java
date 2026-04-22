package com.seedcrm.crm.salary.service;

import com.seedcrm.crm.salary.dto.SalarySettlementCreateRequest;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.enums.SalarySettlementStatus;

public interface SettlementService {

    SalarySettlement createSettlement(SalarySettlementCreateRequest request);

    SalarySettlement updateStatus(Long settlementId, SalarySettlementStatus targetStatus);
}
