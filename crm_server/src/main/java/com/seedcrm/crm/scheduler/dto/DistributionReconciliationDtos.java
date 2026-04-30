package com.seedcrm.crm.scheduler.dto;

import java.time.LocalDateTime;
import lombok.Data;

public final class DistributionReconciliationDtos {

    private DistributionReconciliationDtos() {
    }

    @Data
    public static class DistributionReconciliationResult {
        private Long orderId;
        private String externalOrderId;
        private String partnerCode;
        private String jobType;
        private String action;
        private String status;
        private String eventType;
        private String idempotencyKey;
        private String processStatus;
        private String message;
        private LocalDateTime checkedAt;
    }
}
