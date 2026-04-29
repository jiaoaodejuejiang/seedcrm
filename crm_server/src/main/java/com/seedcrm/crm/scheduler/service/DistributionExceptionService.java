package com.seedcrm.crm.scheduler.service;

import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventRequest;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import java.util.List;

public interface DistributionExceptionService {

    void recordFailure(String partnerCode,
                       DistributionEventRequest event,
                       String rawPayload,
                       String traceId,
                       String idempotencyKey,
                       String errorCode,
                       String errorMessage);

    List<DistributionExceptionRecord> list(String status);

    DistributionExceptionRecord retry(Long id, PermissionRequestContext context, String remark);

    DistributionExceptionRecord markHandled(Long id, PermissionRequestContext context, String remark);
}
