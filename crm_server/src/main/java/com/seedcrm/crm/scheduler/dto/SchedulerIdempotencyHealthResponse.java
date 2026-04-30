package com.seedcrm.crm.scheduler.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class SchedulerIdempotencyHealthResponse {

    private LocalDateTime generatedAt;
    private String tableName;
    private String providerCode;
    private boolean healthy;
    private String status;
    private long duplicateGroupCount;
    private long affectedLogCount;
    private List<IndexHealth> indexes;
    private List<DuplicateGroup> duplicateGroups;
    private List<String> recommendedActions;

    @Data
    public static class IndexHealth {
        private String indexName;
        private String columns;
        private boolean unique;
        private boolean exists;
        private String status;
        private String message;
    }

    @Data
    public static class DuplicateGroup {
        private String duplicateType;
        private String providerCode;
        private String duplicateKey;
        private long duplicateCount;
        private Long firstLogId;
        private Long latestLogId;
        private Long retainLogId;
        private List<Long> reviewLogIds;
        private List<DuplicateLogSample> logSamples;
        private LocalDateTime firstReceivedAt;
        private LocalDateTime latestReceivedAt;
        private List<String> sampleTraceIds;
        private String cleanupStrategy;
        private String recommendedAction;
    }

    @Data
    public static class DuplicateLogSample {
        private Long id;
        private String traceId;
        private String processStatus;
        private String idempotencyStatus;
        private String bodyHash;
        private Long relatedOrderId;
        private String receivedAt;
    }
}
