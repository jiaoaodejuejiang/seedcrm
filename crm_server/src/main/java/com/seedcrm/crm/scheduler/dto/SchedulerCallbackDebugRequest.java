package com.seedcrm.crm.scheduler.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class SchedulerCallbackDebugRequest {

    private String providerCode;

    private String callbackName;

    private String callbackPath;

    private String requestMethod = "POST";

    private Map<String, String> parameters = new LinkedHashMap<>();

    private String payload;
}
