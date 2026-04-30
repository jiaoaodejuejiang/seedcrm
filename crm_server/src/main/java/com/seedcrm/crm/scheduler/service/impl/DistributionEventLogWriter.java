package com.seedcrm.crm.scheduler.service.impl;

import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DistributionEventLogWriter {

    private final IntegrationCallbackEventLogMapper eventLogMapper;

    public DistributionEventLogWriter(IntegrationCallbackEventLogMapper eventLogMapper) {
        this.eventLogMapper = eventLogMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(IntegrationCallbackEventLog log) {
        eventLogMapper.insert(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeRequiresNew(IntegrationCallbackEventLog log) {
        eventLogMapper.insert(log);
    }
}
