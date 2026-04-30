package com.seedcrm.crm.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class SchedulerMonitorSummaryResponse {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;
    private String overallStatus;
    private QueueSummary outbox;
    private ExceptionSummary exceptions;
    private IdempotencySummary idempotency;
    private JobSummary jobs;
    private List<JobBatchSummary> recentBatches;
    private List<String> recommendedActions;

    @Data
    public static class QueueSummary {
        private long pending;
        private long processing;
        private long success;
        private long failed;
        private long deadLetter;
        private long totalAttention;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime latestAttentionAt;
    }

    @Data
    public static class ExceptionSummary {
        private long open;
        private long retryQueued;
        private long handled;
        private long totalAttention;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime latestAttentionAt;
    }

    @Data
    public static class IdempotencySummary {
        private boolean healthy;
        private String status;
        private long duplicateGroupCount;
        private long affectedLogCount;
    }

    @Data
    public static class JobSummary {
        private long total24h;
        private long success24h;
        private long failed24h;
        private long running24h;
        private long queued24h;
        private Integer successRate24h;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime latestFailedAt;
    }

    @Data
    public static class JobBatchSummary {
        private Long logId;
        private String jobCode;
        private String jobName;
        private String triggerType;
        private String status;
        private Integer processedCount;
        private Integer replayedCount;
        private Integer noChangeCount;
        private Integer failedCount;
        private String resultSummary;
        private String recommendedAction;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startedAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime finishedAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
