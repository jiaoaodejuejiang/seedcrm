package com.seedcrm.crm.scheduler.dto;

import lombok.Data;

@Data
public class SchedulerTriggerRequest {

    private String jobCode;

    private String payload;
}
