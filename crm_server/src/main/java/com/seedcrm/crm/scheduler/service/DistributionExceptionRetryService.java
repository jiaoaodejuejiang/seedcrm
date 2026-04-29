package com.seedcrm.crm.scheduler.service;

import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import java.util.List;

public interface DistributionExceptionRetryService {

    List<DistributionExceptionRecord> processRetryQueue(int limit);
}
