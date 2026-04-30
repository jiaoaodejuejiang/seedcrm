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
        private LocalDateTime firstReceivedAt;
        private LocalDateTime latestReceivedAt;
        private List<String> sampleTraceIds;
        private String recommendedAction;
    }
}
