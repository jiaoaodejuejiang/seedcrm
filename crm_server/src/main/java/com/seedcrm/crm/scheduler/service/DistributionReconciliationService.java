package com.seedcrm.crm.scheduler.service;

import com.seedcrm.crm.scheduler.dto.DistributionReconciliationDtos.DistributionReconciliationResult;
import java.util.List;

public interface DistributionReconciliationService {

    List<DistributionReconciliationResult> checkOrderStatus(int limit);

    List<DistributionReconciliationResult> pullReconciliation(int limit);
}
