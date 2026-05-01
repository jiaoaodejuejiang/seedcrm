package com.seedcrm.crm.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class SchedulerGoLiveReadinessResponse {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;
    private String providerCode;
    private String overallStatus;
    private String environmentMode;
    private List<ReadinessCheck> checks;
    private List<String> blockers;
    private List<String> recommendedActions;

    @Data
    public static class ReadinessCheck {
        private String checkCode;
        private String title;
        private String status;
        private String severity;
        private String currentValue;
        private String expectedValue;
        private String impact;
        private String recommendedAction;
    }
}
