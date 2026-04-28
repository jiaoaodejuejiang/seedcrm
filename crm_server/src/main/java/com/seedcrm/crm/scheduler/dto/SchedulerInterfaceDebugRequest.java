package com.seedcrm.crm.scheduler.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class SchedulerInterfaceDebugRequest {

    private String mode = "MOCK";

    private String providerCode;

    private String interfaceCode;

    private String requestMethod = "POST";

    private String path;

    private Map<String, Object> parameters = new LinkedHashMap<>();

    private String payload;
}
