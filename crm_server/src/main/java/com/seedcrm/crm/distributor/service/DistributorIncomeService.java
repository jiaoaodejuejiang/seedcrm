package com.seedcrm.crm.distributor.service;

import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;

public interface DistributorIncomeService {

    DistributorIncomeDetail calculate(Long orderId);
}
