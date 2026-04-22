package com.seedcrm.crm.distributor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seedcrm.crm.distributor.dto.DistributorCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorStatsResponse;
import com.seedcrm.crm.distributor.entity.Distributor;

public interface DistributorService extends IService<Distributor> {

    Distributor createDistributor(DistributorCreateRequest request);

    Distributor getByIdOrThrow(Long distributorId);

    DistributorStatsResponse getStats(Long distributorId);
}
