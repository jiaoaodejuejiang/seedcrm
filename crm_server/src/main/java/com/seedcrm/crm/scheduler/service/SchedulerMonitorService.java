package com.seedcrm.crm.scheduler.service;

import com.seedcrm.crm.scheduler.dto.SchedulerMonitorSummaryResponse;

public interface SchedulerMonitorService {

    SchedulerMonitorSummaryResponse summarize(String providerCode);
}
