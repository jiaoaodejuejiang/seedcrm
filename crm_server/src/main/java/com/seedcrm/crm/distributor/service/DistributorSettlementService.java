package com.seedcrm.crm.distributor.service;

import com.seedcrm.crm.distributor.dto.DistributorSettlementCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorWithdrawCreateRequest;
import com.seedcrm.crm.distributor.entity.DistributorSettlement;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.distributor.enums.DistributorSettlementStatus;
import com.seedcrm.crm.distributor.enums.DistributorWithdrawStatus;
import java.math.BigDecimal;

public interface DistributorSettlementService {

    DistributorSettlement createSettlement(DistributorSettlementCreateRequest request);

    DistributorSettlement updateSettlementStatus(Long settlementId, DistributorSettlementStatus targetStatus);

    BigDecimal getWithdrawableAmount(Long distributorId);

    DistributorWithdraw createWithdraw(DistributorWithdrawCreateRequest request);

    DistributorWithdraw approveWithdraw(Long withdrawId, DistributorWithdrawStatus targetStatus);
}
