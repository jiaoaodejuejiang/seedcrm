package com.seedcrm.crm.systemconfig.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SystemConfigRuntimeEventScheduler {

    private final SystemConfigService systemConfigService;

    public SystemConfigRuntimeEventScheduler(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @Scheduled(
            fixedDelayString = "${seedcrm.system-config.runtime-event.fixed-delay-ms:30000}",
            initialDelayString = "${seedcrm.system-config.runtime-event.initial-delay-ms:30000}")
    public void processDueRuntimeEvents() {
        try {
            systemConfigService.processDueRuntimeEvents(20);
        } catch (Exception exception) {
            log.warn("system config runtime event processing failed: {}", exception.getMessage());
        }
    }
}
