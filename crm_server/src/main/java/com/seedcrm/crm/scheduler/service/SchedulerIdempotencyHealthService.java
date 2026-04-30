package com.seedcrm.crm.scheduler.service;

import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse;

public interface SchedulerIdempotencyHealthService {

    SchedulerIdempotencyHealthResponse inspect(String providerCode);
}
