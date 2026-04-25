package com.seedcrm.crm.scheduler.dto;

import lombok.Data;

@Data
public class SchedulerJobUpsertRequest {

    private String jobCode;

    private String moduleCode;

    private String syncMode;

    private Integer intervalMinutes;

    private Integer retryLimit;

    private String queueName;

    private Long providerId;

    private String endpoint;

    private String status;
}
