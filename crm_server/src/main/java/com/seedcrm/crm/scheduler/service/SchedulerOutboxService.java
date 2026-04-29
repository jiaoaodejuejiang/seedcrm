package com.seedcrm.crm.scheduler.service;

import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import java.util.List;

public interface SchedulerOutboxService {

    SchedulerOutboxEvent enqueueFulfillmentEvent(Order order, PlanOrder planOrder, String eventType);

    List<SchedulerOutboxEvent> list(String status);

    SchedulerOutboxEvent retry(Long id, PermissionRequestContext context);

    List<SchedulerOutboxEvent> processDue(int limit);
}
