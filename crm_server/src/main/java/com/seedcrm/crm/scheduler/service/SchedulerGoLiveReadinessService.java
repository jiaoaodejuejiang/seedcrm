package com.seedcrm.crm.scheduler.service;

import com.seedcrm.crm.scheduler.dto.SchedulerGoLiveReadinessResponse;

public interface SchedulerGoLiveReadinessService {

    SchedulerGoLiveReadinessResponse inspect(String providerCode);
}
