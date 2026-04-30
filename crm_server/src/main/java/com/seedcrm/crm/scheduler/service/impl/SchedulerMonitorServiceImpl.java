package com.seedcrm.crm.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerMonitorSummaryResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerMonitorSummaryResponse.ExceptionSummary;
import com.seedcrm.crm.scheduler.dto.SchedulerMonitorSummaryResponse.IdempotencySummary;
import com.seedcrm.crm.scheduler.dto.SchedulerMonitorSummaryResponse.JobBatchSummary;
import com.seedcrm.crm.scheduler.dto.SchedulerMonitorSummaryResponse.JobSummary;
import com.seedcrm.crm.scheduler.dto.SchedulerMonitorSummaryResponse.QueueSummary;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import com.seedcrm.crm.scheduler.mapper.DistributionExceptionRecordMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobLogMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerOutboxEventMapper;
import com.seedcrm.crm.scheduler.service.SchedulerIdempotencyHealthService;
import com.seedcrm.crm.scheduler.service.SchedulerMonitorService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SchedulerMonitorServiceImpl implements SchedulerMonitorService {

    private static final String PROVIDER_DISTRIBUTION = "DISTRIBUTION";
    private static final List<String> DISTRIBUTION_JOB_CODES = List.of(
            "DISTRIBUTION_OUTBOX_PROCESS",
            "DISTRIBUTION_EXCEPTION_RETRY",
            "DISTRIBUTION_STATUS_CHECK",
            "DISTRIBUTION_RECONCILE_PULL");

    private final SchedulerOutboxEventMapper schedulerOutboxEventMapper;
    private final DistributionExceptionRecordMapper distributionExceptionRecordMapper;
    private final SchedulerJobLogMapper schedulerJobLogMapper;
    private final SchedulerIdempotencyHealthService schedulerIdempotencyHealthService;
    private final ObjectMapper objectMapper;

    public SchedulerMonitorServiceImpl(SchedulerOutboxEventMapper schedulerOutboxEventMapper,
                                       DistributionExceptionRecordMapper distributionExceptionRecordMapper,
                                       SchedulerJobLogMapper schedulerJobLogMapper,
                                       SchedulerIdempotencyHealthService schedulerIdempotencyHealthService,
                                       ObjectMapper objectMapper) {
        this.schedulerOutboxEventMapper = schedulerOutboxEventMapper;
        this.distributionExceptionRecordMapper = distributionExceptionRecordMapper;
        this.schedulerJobLogMapper = schedulerJobLogMapper;
        this.schedulerIdempotencyHealthService = schedulerIdempotencyHealthService;
        this.objectMapper = objectMapper;
    }

    @Override
    public SchedulerMonitorSummaryResponse summarize(String providerCode) {
        String normalizedProviderCode = normalizeProvider(providerCode);
        SchedulerMonitorSummaryResponse response = new SchedulerMonitorSummaryResponse();
        response.setGeneratedAt(LocalDateTime.now());
        response.setOutbox(outboxSummary(normalizedProviderCode));
        response.setExceptions(exceptionSummary(normalizedProviderCode));
        response.setIdempotency(idempotencySummary(normalizedProviderCode));
        response.setJobs(jobSummary());
        response.setRecentBatches(recentBatches());
        response.setRecommendedActions(recommendations(response));
        response.setOverallStatus(overallStatus(response));
        return response;
    }

    private QueueSummary outboxSummary(String providerCode) {
        QueueSummary summary = new QueueSummary();
        summary.setPending(countOutbox(providerCode, "PENDING"));
        summary.setProcessing(countOutbox(providerCode, "PROCESSING"));
        summary.setSuccess(countOutbox(providerCode, "SUCCESS"));
        summary.setFailed(countOutbox(providerCode, "FAILED"));
        summary.setDeadLetter(countOutbox(providerCode, "DEAD_LETTER"));
        summary.setTotalAttention(summary.getPending() + summary.getFailed() + summary.getDeadLetter());
        SchedulerOutboxEvent latest = latestAttentionOutbox(providerCode);
        summary.setLatestAttentionAt(latest == null ? null : firstNonNull(latest.getUpdatedAt(), latest.getCreatedAt()));
        return summary;
    }

    private long countOutbox(String providerCode, String status) {
        Long count = schedulerOutboxEventMapper.selectCount(Wrappers.<SchedulerOutboxEvent>lambdaQuery()
                .eq(StringUtils.hasText(providerCode), SchedulerOutboxEvent::getProviderCode, providerCode)
                .eq(SchedulerOutboxEvent::getStatus, status));
        return safeCount(count);
    }

    private SchedulerOutboxEvent latestAttentionOutbox(String providerCode) {
        return schedulerOutboxEventMapper.selectList(Wrappers.<SchedulerOutboxEvent>lambdaQuery()
                        .eq(StringUtils.hasText(providerCode), SchedulerOutboxEvent::getProviderCode, providerCode)
                        .in(SchedulerOutboxEvent::getStatus, List.of("PENDING", "FAILED", "DEAD_LETTER"))
                        .orderByDesc(SchedulerOutboxEvent::getUpdatedAt)
                        .orderByDesc(SchedulerOutboxEvent::getCreatedAt)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private ExceptionSummary exceptionSummary(String partnerCode) {
        ExceptionSummary summary = new ExceptionSummary();
        summary.setOpen(countException(partnerCode, "OPEN"));
        summary.setRetryQueued(countException(partnerCode, "RETRY_QUEUED"));
        summary.setHandled(countException(partnerCode, "HANDLED"));
        summary.setTotalAttention(summary.getOpen() + summary.getRetryQueued());
        DistributionExceptionRecord latest = latestAttentionException(partnerCode);
        summary.setLatestAttentionAt(latest == null ? null : firstNonNull(latest.getUpdatedAt(), latest.getCreatedAt()));
        return summary;
    }

    private long countException(String partnerCode, String status) {
        Long count = distributionExceptionRecordMapper.selectCount(Wrappers.<DistributionExceptionRecord>lambdaQuery()
                .eq(StringUtils.hasText(partnerCode), DistributionExceptionRecord::getPartnerCode, partnerCode)
                .eq(DistributionExceptionRecord::getHandlingStatus, status));
        return safeCount(count);
    }

    private DistributionExceptionRecord latestAttentionException(String partnerCode) {
        return distributionExceptionRecordMapper.selectList(Wrappers.<DistributionExceptionRecord>lambdaQuery()
                        .eq(StringUtils.hasText(partnerCode), DistributionExceptionRecord::getPartnerCode, partnerCode)
                        .in(DistributionExceptionRecord::getHandlingStatus, List.of("OPEN", "RETRY_QUEUED"))
                        .orderByDesc(DistributionExceptionRecord::getUpdatedAt)
                        .orderByDesc(DistributionExceptionRecord::getCreatedAt)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private IdempotencySummary idempotencySummary(String providerCode) {
        SchedulerIdempotencyHealthResponse health = schedulerIdempotencyHealthService.inspect(
                StringUtils.hasText(providerCode) ? providerCode : PROVIDER_DISTRIBUTION);
        IdempotencySummary summary = new IdempotencySummary();
        summary.setHealthy(health.isHealthy());
        summary.setStatus(health.getStatus());
        summary.setDuplicateGroupCount(health.getDuplicateGroupCount());
        summary.setAffectedLogCount(health.getAffectedLogCount());
        return summary;
    }

    private JobSummary jobSummary() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        JobSummary summary = new JobSummary();
        summary.setTotal24h(countLogs(since, null));
        summary.setSuccess24h(countLogs(since, "SUCCESS"));
        summary.setFailed24h(countLogs(since, "FAILED"));
        summary.setRunning24h(countLogs(since, "RUNNING"));
        summary.setQueued24h(countLogs(since, "QUEUED"));
        summary.setSuccessRate24h(successRate(summary.getSuccess24h(), summary.getTotal24h()));
        SchedulerJobLog latestFailedLog = latestFailedLog();
        summary.setLatestFailedAt(latestFailedLog == null ? null : firstNonNull(latestFailedLog.getFinishedAt(), latestFailedLog.getCreatedAt()));
        return summary;
    }

    private long countLogs(LocalDateTime since, String status) {
        LambdaQueryWrapper<SchedulerJobLog> wrapper = Wrappers.<SchedulerJobLog>lambdaQuery()
                .in(SchedulerJobLog::getJobCode, DISTRIBUTION_JOB_CODES)
                .ge(SchedulerJobLog::getCreatedAt, since);
        if (StringUtils.hasText(status)) {
            wrapper.eq(SchedulerJobLog::getStatus, status);
        }
        return safeCount(schedulerJobLogMapper.selectCount(wrapper));
    }

    private SchedulerJobLog latestFailedLog() {
        return schedulerJobLogMapper.selectList(Wrappers.<SchedulerJobLog>lambdaQuery()
                        .in(SchedulerJobLog::getJobCode, DISTRIBUTION_JOB_CODES)
                        .eq(SchedulerJobLog::getStatus, "FAILED")
                        .orderByDesc(SchedulerJobLog::getFinishedAt)
                        .orderByDesc(SchedulerJobLog::getCreatedAt)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<JobBatchSummary> recentBatches() {
        return schedulerJobLogMapper.selectList(Wrappers.<SchedulerJobLog>lambdaQuery()
                        .in(SchedulerJobLog::getJobCode, DISTRIBUTION_JOB_CODES)
                        .orderByDesc(SchedulerJobLog::getCreatedAt)
                        .orderByDesc(SchedulerJobLog::getId)
                        .last("LIMIT 8"))
                .stream()
                .map(this::batchSummary)
                .toList();
    }

    private JobBatchSummary batchSummary(SchedulerJobLog log) {
        JsonNode payload = parsePayload(log.getPayload());
        JsonNode actionCounts = payload == null ? null : payload.get("actionCounts");
        JobBatchSummary summary = new JobBatchSummary();
        summary.setLogId(log.getId());
        summary.setJobCode(log.getJobCode());
        summary.setJobName(jobName(log.getJobCode()));
        summary.setTriggerType(log.getTriggerType());
        summary.setStatus(log.getStatus());
        summary.setProcessedCount(firstInteger(log.getImportedCount(), intValue(payload, "processedCount"), intValue(payload, "importedCount")));
        summary.setReplayedCount(intValue(actionCounts, "replayed"));
        summary.setNoChangeCount(intValue(actionCounts, "noChange"));
        summary.setFailedCount(firstInteger(intValue(actionCounts, "failed"), "FAILED".equals(normalize(log.getStatus())) ? 1 : null));
        summary.setResultSummary(resultSummary(log, summary));
        summary.setRecommendedAction(recommendedAction(log, summary));
        summary.setStartedAt(log.getStartedAt());
        summary.setFinishedAt(log.getFinishedAt());
        summary.setCreatedAt(log.getCreatedAt());
        return summary;
    }

    private String resultSummary(SchedulerJobLog log, JobBatchSummary summary) {
        if (StringUtils.hasText(log.getErrorMessage())) {
            return log.getErrorMessage();
        }
        if (summary.getFailedCount() != null && summary.getFailedCount() > 0) {
            return "本批次存在失败记录，请进入异常队列核对";
        }
        if (summary.getProcessedCount() != null) {
            return "本批次处理 " + summary.getProcessedCount() + " 条记录";
        }
        return "等待执行或暂无处理结果";
    }

    private String recommendedAction(SchedulerJobLog log, JobBatchSummary summary) {
        String status = normalize(log.getStatus());
        if ("FAILED".equals(status)) {
            return "查看失败原因，修正配置或数据后重新入队";
        }
        if (summary.getFailedCount() != null && summary.getFailedCount() > 0) {
            return "进入分销异常队列查看失败明细";
        }
        if ("QUEUED".equals(status) || "RUNNING".equals(status)) {
            return "等待调度器完成执行后刷新结果";
        }
        return "无需处理";
    }

    private List<String> recommendations(SchedulerMonitorSummaryResponse response) {
        List<String> actions = new ArrayList<>();
        if (response.getOutbox().getFailed() > 0 || response.getOutbox().getDeadLetter() > 0) {
            actions.add("履约回推存在失败或死信，请进入履约回推队列核对回推地址、签名与外部接口响应。");
        }
        if (response.getExceptions().getOpen() > 0) {
            actions.add("分销异常队列仍有待处理记录，请按处理建议修正数据或重新入队。");
        }
        if (!response.getIdempotency().isHealthy()) {
            actions.add("幂等健康未完全通过，请先治理重复接收日志，不要删除 Customer / Order / PlanOrder 主链路数据。");
        }
        if (response.getJobs().getFailed24h() > 0) {
            actions.add("近 24 小时存在失败调度批次，请查看执行监控并进入对应队列处理。");
        }
        if (actions.isEmpty()) {
            actions.add("当前分销调度链路运行正常，继续保持自动调度即可。");
        }
        return actions;
    }

    private String overallStatus(SchedulerMonitorSummaryResponse response) {
        if (response.getOutbox().getDeadLetter() > 0
                || response.getExceptions().getOpen() > 0
                || response.getJobs().getFailed24h() > 0) {
            return "ATTENTION";
        }
        if (!response.getIdempotency().isHealthy() || response.getOutbox().getPending() > 0 || response.getExceptions().getRetryQueued() > 0) {
            return "WARNING";
        }
        return "HEALTHY";
    }

    private JsonNode parsePayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer intValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || !node.get(fieldName).canConvertToInt()) {
            return null;
        }
        return node.get(fieldName).asInt();
    }

    private Integer firstInteger(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer successRate(long success, long total) {
        if (total <= 0) {
            return null;
        }
        return (int) Math.round((success * 100.0d) / total);
    }

    private long safeCount(Long count) {
        return count == null ? 0L : count;
    }

    private LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private String jobName(String jobCode) {
        return switch (normalize(jobCode)) {
            case "DISTRIBUTION_OUTBOX_PROCESS" -> "分销履约回推";
            case "DISTRIBUTION_EXCEPTION_RETRY" -> "分销异常重试";
            case "DISTRIBUTION_STATUS_CHECK" -> "分销状态回查";
            case "DISTRIBUTION_RECONCILE_PULL" -> "分销对账拉取";
            default -> StringUtils.hasText(jobCode) ? jobCode : "--";
        };
    }

    private String normalizeProvider(String providerCode) {
        return StringUtils.hasText(providerCode) ? providerCode.trim().toUpperCase(Locale.ROOT) : PROVIDER_DISTRIBUTION;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
