package com.seedcrm.crm.distributor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seedcrm.crm.distributor.dto.DistributorCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorRuleCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorStatsResponse;
import com.seedcrm.crm.distributor.entity.Distributor;
import com.seedcrm.crm.distributor.entity.DistributorRule;

public interface DistributorService extends IService<Distributor> {

    Distributor createDistributor(DistributorCreateRequest request);

    DistributorRule saveRule(DistributorRuleCreateRequest request);

    Distributor getByIdOrThrow(Long distributorId);

    DistributorStatsResponse getStats(Long distributorId);
}
