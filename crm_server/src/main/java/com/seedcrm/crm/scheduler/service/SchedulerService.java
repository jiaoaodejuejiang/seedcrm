package com.seedcrm.crm.scheduler.service;

import com.seedcrm.crm.scheduler.dto.SchedulerJobUpsertRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerTriggerRequest;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.entity.SchedulerJob;
import com.seedcrm.crm.scheduler.entity.SchedulerJobAuditLog;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import java.util.List;

public interface SchedulerService {

    List<SchedulerJob> listJobs();

    List<SchedulerJobLog> listLogs(String jobCode);

    List<SchedulerJobAuditLog> listAuditLogs(String jobCode);

    SchedulerJob saveJob(SchedulerJobUpsertRequest request, PermissionRequestContext context);

    SchedulerJobLog trigger(SchedulerTriggerRequest request, PermissionRequestContext context);

    SchedulerJobLog dryRun(SchedulerTriggerRequest request, PermissionRequestContext context);

    List<SchedulerJobLog> retryFailed(String jobCode, PermissionRequestContext context);

    SchedulerJobLog retryLog(Long logId, PermissionRequestContext context);
}
