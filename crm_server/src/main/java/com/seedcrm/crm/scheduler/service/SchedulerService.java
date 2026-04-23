package com.seedcrm.crm.scheduler.service;

import com.seedcrm.crm.scheduler.dto.SchedulerJobUpsertRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerTriggerRequest;
import com.seedcrm.crm.scheduler.entity.SchedulerJob;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import java.util.List;

public interface SchedulerService {

    List<SchedulerJob> listJobs();

    List<SchedulerJobLog> listLogs(String jobCode);

    SchedulerJob saveJob(SchedulerJobUpsertRequest request);

    SchedulerJobLog trigger(SchedulerTriggerRequest request);

    List<SchedulerJobLog> retryFailed(String jobCode);
}
