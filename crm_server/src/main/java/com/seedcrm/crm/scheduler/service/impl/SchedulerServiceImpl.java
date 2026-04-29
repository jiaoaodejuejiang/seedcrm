package com.seedcrm.crm.scheduler.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.clue.service.DouyinClueSyncService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.dto.SchedulerJobUpsertRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerTriggerRequest;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.entity.SchedulerJob;
import com.seedcrm.crm.scheduler.entity.SchedulerJobAuditLog;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobAuditLogMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobLogMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobMapper;
import com.seedcrm.crm.scheduler.service.DistributionExceptionRetryService;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import com.seedcrm.crm.scheduler.service.SchedulerOutboxService;
import com.seedcrm.crm.scheduler.service.SchedulerService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SchedulerServiceImpl implements SchedulerService {

    private static final int JOB_LOCK_SECONDS = 30;
    private static final String JOB_DISTRIBUTION_OUTBOX_PROCESS = "DISTRIBUTION_OUTBOX_PROCESS";
    private static final String JOB_DISTRIBUTION_EXCEPTION_RETRY = "DISTRIBUTION_EXCEPTION_RETRY";
    private static final Set<String> DISTRIBUTION_JOB_CODES = Set.of(
            JOB_DISTRIBUTION_OUTBOX_PROCESS,
            JOB_DISTRIBUTION_EXCEPTION_RETRY);

    private final SchedulerJobMapper schedulerJobMapper;
    private final SchedulerJobLogMapper schedulerJobLogMapper;
    private final SchedulerJobAuditLogMapper schedulerJobAuditLogMapper;
    private final DouyinClueSyncService douyinClueSyncService;
    private final SchedulerIntegrationService schedulerIntegrationService;
    private final SchedulerOutboxService schedulerOutboxService;
    private final DistributionExceptionRetryService distributionExceptionRetryService;

    public SchedulerServiceImpl(SchedulerJobMapper schedulerJobMapper,
                                SchedulerJobLogMapper schedulerJobLogMapper,
                                SchedulerJobAuditLogMapper schedulerJobAuditLogMapper,
                                DouyinClueSyncService douyinClueSyncService,
                                SchedulerIntegrationService schedulerIntegrationService,
                                SchedulerOutboxService schedulerOutboxService,
                                DistributionExceptionRetryService distributionExceptionRetryService) {
        this.schedulerJobMapper = schedulerJobMapper;
        this.schedulerJobLogMapper = schedulerJobLogMapper;
        this.schedulerJobAuditLogMapper = schedulerJobAuditLogMapper;
        this.douyinClueSyncService = douyinClueSyncService;
        this.schedulerIntegrationService = schedulerIntegrationService;
        this.schedulerOutboxService = schedulerOutboxService;
        this.distributionExceptionRetryService = distributionExceptionRetryService;
    }

    @Override
    public List<SchedulerJob> listJobs() {
        return schedulerJobMapper.selectList(Wrappers.<SchedulerJob>lambdaQuery()
                        .orderByAsc(SchedulerJob::getModuleCode)
                        .orderByAsc(SchedulerJob::getJobCode)
                        .orderByAsc(SchedulerJob::getId))
                .stream()
                .peek(job -> job.setStatus(canonicalStatus(job.getStatus())))
                .toList();
    }

    @Override
    public List<SchedulerJobLog> listLogs(String jobCode) {
        return schedulerJobLogMapper.selectList(Wrappers.<SchedulerJobLog>lambdaQuery()
                .eq(StringUtils.hasText(jobCode), SchedulerJobLog::getJobCode, normalize(jobCode))
                .orderByDesc(SchedulerJobLog::getCreatedAt)
                .orderByDesc(SchedulerJobLog::getId));
    }

    @Override
    public List<SchedulerJobAuditLog> listAuditLogs(String jobCode) {
        return schedulerJobAuditLogMapper.selectList(Wrappers.<SchedulerJobAuditLog>lambdaQuery()
                .eq(StringUtils.hasText(jobCode), SchedulerJobAuditLog::getJobCode, normalize(jobCode))
                .orderByDesc(SchedulerJobAuditLog::getCreatedAt)
                .orderByDesc(SchedulerJobAuditLog::getId)
                .last("LIMIT 100"));
    }

    @Override
    public SchedulerJob saveJob(SchedulerJobUpsertRequest request, PermissionRequestContext context) {
        if (request == null
                || !StringUtils.hasText(request.getJobCode())
                || !StringUtils.hasText(request.getModuleCode())) {
            throw new BusinessException("jobCode and moduleCode are required");
        }
        validateJobScope(request);
        String jobCode = normalize(request.getJobCode());
        SchedulerJob existing = schedulerJobMapper.selectOne(Wrappers.<SchedulerJob>lambdaQuery()
                .eq(SchedulerJob::getJobCode, jobCode)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            SchedulerJob job = new SchedulerJob();
            apply(job, request);
            job.setJobCode(jobCode);
            job.setCreatedAt(now);
            job.setUpdatedAt(now);
            job.setNextRunTime(now.plusMinutes(job.getIntervalMinutes() == null ? 1 : job.getIntervalMinutes()));
            if (schedulerJobMapper.insert(job) <= 0) {
                throw new BusinessException("failed to save scheduler job");
            }
            audit(job.getJobCode(), null, "JOB_CREATE", context, "SUCCESS", "任务配置已创建", describeJob(job));
            return job;
        }
        apply(existing, request);
        existing.setUpdatedAt(now);
        if (schedulerJobMapper.updateById(existing) <= 0) {
            throw new BusinessException("failed to update scheduler job");
        }
        audit(existing.getJobCode(), null, "JOB_UPDATE", context, "SUCCESS", "任务配置已更新", describeJob(existing));
        return existing;
    }

    @Override
    public SchedulerJobLog trigger(SchedulerTriggerRequest request, PermissionRequestContext context) {
        if (request == null || !StringUtils.hasText(request.getJobCode())) {
            throw new BusinessException("jobCode is required");
        }
        SchedulerJob job = getJobOrThrow(request.getJobCode());
        if (!isEnabled(job.getStatus())) {
            throw new BusinessException("scheduler job is not enabled");
        }
        LocalDateTime now = LocalDateTime.now();
        job.setLastRunTime(now);
        job.setNextRunTime(now.plusMinutes(job.getIntervalMinutes() == null ? 1 : job.getIntervalMinutes()));
        job.setUpdatedAt(now);
        schedulerJobMapper.updateById(job);

        String payload = StringUtils.hasText(request.getPayload()) ? request.getPayload().trim() : "{\"source\":\"manual\"}";
        SchedulerJobLog log = enqueue(job, payload, "MANUAL", now);
        audit(job.getJobCode(), log.getId(), "JOB_TRIGGER", context, "QUEUED", "手动提交客资同步任务", trimDetail(payload));
        return schedulerJobLogMapper.selectById(log.getId());
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void dispatchDueJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<SchedulerJob> dueJobs = schedulerJobMapper.selectList(Wrappers.<SchedulerJob>lambdaQuery()
                .in(SchedulerJob::getStatus, List.of("ACTIVE", "ENABLED"))
                .and(wrapper -> wrapper.isNull(SchedulerJob::getNextRunTime)
                        .or()
                        .le(SchedulerJob::getNextRunTime, now))
                .orderByAsc(SchedulerJob::getId));
        for (SchedulerJob job : dueJobs) {
            if (hasPendingLog(job.getJobCode())) {
                continue;
            }
            if (!tryClaimDueJob(job, now)) {
                continue;
            }
            SchedulerJobLog log = null;
            try {
                job.setStatus(canonicalStatus(job.getStatus()));
                job.setLastRunTime(now);
                job.setNextRunTime(now.plusMinutes(job.getIntervalMinutes() == null ? 1 : job.getIntervalMinutes()));
                job.setUpdatedAt(now);
                log = enqueue(job, "{\"source\":\"auto\"}", "AUTO", now);
                audit(job.getJobCode(), log.getId(), "JOB_AUTO_ENQUEUE", null, "QUEUED", "自动提交客资同步任务", null);
            } finally {
                releaseDueJobLock(job);
            }
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 8000)
    public void processQueuedLogs() {
        LocalDateTime now = LocalDateTime.now();
        List<SchedulerJobLog> queuedLogs = schedulerJobLogMapper.selectList(Wrappers.<SchedulerJobLog>lambdaQuery()
                .eq(SchedulerJobLog::getStatus, "QUEUED")
                .and(wrapper -> wrapper.isNull(SchedulerJobLog::getNextRetryTime)
                        .or()
                        .le(SchedulerJobLog::getNextRetryTime, now))
                .orderByAsc(SchedulerJobLog::getCreatedAt)
                .orderByAsc(SchedulerJobLog::getId)
                .last("LIMIT 20"));
        for (SchedulerJobLog log : queuedLogs) {
            if (log.getNextRetryTime() != null && log.getNextRetryTime().isAfter(now)) {
                continue;
            }
            execute(log);
        }
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 12000)
    public void requeueDueFailedLogs() {
        LocalDateTime now = LocalDateTime.now();
        List<SchedulerJobLog> failedLogs = schedulerJobLogMapper.selectList(Wrappers.<SchedulerJobLog>lambdaQuery()
                .eq(SchedulerJobLog::getStatus, "FAILED")
                .isNotNull(SchedulerJobLog::getNextRetryTime)
                .le(SchedulerJobLog::getNextRetryTime, now)
                .orderByAsc(SchedulerJobLog::getNextRetryTime)
                .orderByAsc(SchedulerJobLog::getId)
                .last("LIMIT 20"));
        for (SchedulerJobLog log : failedLogs) {
            SchedulerJob job = getJobOrThrow(log.getJobCode());
            if (exceedsRetryLimit(log, job)) {
                continue;
            }
            log.setRetryCount(log.getRetryCount() == null ? 1 : log.getRetryCount() + 1);
            log.setStatus("QUEUED");
            log.setTriggerType("RETRY");
            log.setNextRetryTime(null);
            schedulerJobLogMapper.updateById(log);
        }
    }

    @Override
    public List<SchedulerJobLog> retryFailed(String jobCode, PermissionRequestContext context) {
        SchedulerJob job = getJobOrThrow(jobCode);
        List<SchedulerJobLog> failedLogs = schedulerJobLogMapper.selectList(Wrappers.<SchedulerJobLog>lambdaQuery()
                .eq(SchedulerJobLog::getJobCode, job.getJobCode())
                .eq(SchedulerJobLog::getStatus, "FAILED")
                .orderByAsc(SchedulerJobLog::getCreatedAt)
                .orderByAsc(SchedulerJobLog::getId));
        for (SchedulerJobLog log : failedLogs) {
            if (exceedsRetryLimit(log, job)) {
                continue;
            }
            log.setRetryCount(log.getRetryCount() == null ? 1 : log.getRetryCount() + 1);
            log.setStatus("QUEUED");
            log.setTriggerType("MANUAL_RETRY");
            log.setErrorMessage(null);
            log.setNextRetryTime(null);
            schedulerJobLogMapper.updateById(log);
            audit(job.getJobCode(), log.getId(), "JOB_RETRY", context, "QUEUED", "失败任务已重新入队", "retryCount=" + log.getRetryCount());
        }
        return listLogs(job.getJobCode());
    }

    @Override
    public SchedulerJobLog retryLog(Long logId, PermissionRequestContext context) {
        if (logId == null || logId <= 0) {
            throw new BusinessException("logId is required");
        }
        SchedulerJobLog log = schedulerJobLogMapper.selectById(logId);
        if (log == null) {
            throw new BusinessException("scheduler log not found");
        }
        if (!"FAILED".equals(normalize(log.getStatus()))) {
            throw new BusinessException("only failed scheduler log can be retried");
        }
        SchedulerJob job = getJobOrThrow(log.getJobCode());
        if (exceedsRetryLimit(log, job)) {
            throw new BusinessException("scheduler retry limit reached");
        }
        log.setRetryCount(log.getRetryCount() == null ? 1 : log.getRetryCount() + 1);
        log.setStatus("QUEUED");
        log.setTriggerType("MANUAL_RETRY");
        log.setErrorMessage(null);
        log.setNextRetryTime(null);
        schedulerJobLogMapper.updateById(log);
        audit(job.getJobCode(), log.getId(), "JOB_RETRY", context, "QUEUED", "当前失败记录已重新入队", "retryCount=" + log.getRetryCount());
        return schedulerJobLogMapper.selectById(logId);
    }

    private SchedulerJobLog enqueue(SchedulerJob job, String payload, String triggerType, LocalDateTime now) {
        SchedulerJobLog log = new SchedulerJobLog();
        log.setJobCode(job.getJobCode());
        log.setQueueName(job.getQueueName());
        log.setProviderId(job.getProviderId());
        log.setSyncMode(job.getSyncMode());
        log.setTriggerType(triggerType);
        log.setStatus("QUEUED");
        log.setRetryCount(0);
        log.setPayload(payload);
        log.setCreatedAt(now);
        if (schedulerJobLogMapper.insert(log) <= 0) {
            throw new BusinessException("failed to create scheduler log");
        }
        return log;
    }

    private void execute(SchedulerJobLog log) {
        SchedulerJob job = getJobOrThrow(log.getJobCode());
        IntegrationProviderConfig provider = schedulerIntegrationService.getEnabledProviderOrNull(job.getProviderId());
        LocalDateTime startedAt = LocalDateTime.now();
        if (!tryClaimQueuedLog(log, startedAt)) {
            return;
        }
        log.setStatus("RUNNING");
        log.setErrorMessage(null);
        log.setStartedAt(startedAt);
        log.setFinishedAt(null);
        log.setDurationMs(null);
        audit(job.getJobCode(), log.getId(), "JOB_EXECUTE_START", null, "RUNNING", "开始执行客资同步任务", null);
        try {
            int importedCount = executeSupportedJob(job, provider);
            log.setStatus("SUCCESS");
            log.setPayload("{\"source\":\"sync\",\"importedCount\":" + importedCount + "}");
            log.setImportedCount(importedCount);
            log.setNextRetryTime(null);
            schedulerIntegrationService.markSyncResult(job.getProviderId(), true, "同步成功，导入 " + importedCount + " 条客资");
            audit(job.getJobCode(), log.getId(), "JOB_EXECUTE_SUCCESS", null, "SUCCESS", "客资同步执行成功", "importedCount=" + importedCount);
        } catch (Exception exception) {
            log.setStatus("FAILED");
            log.setErrorMessage(exception.getMessage());
            if (!exceedsRetryLimit(log, job)) {
                log.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
            }
            schedulerIntegrationService.markSyncResult(job.getProviderId(), false, exception.getMessage());
            audit(job.getJobCode(), log.getId(), "JOB_EXECUTE_FAILED", null, "FAILED", "客资同步执行失败", trimDetail(exception.getMessage()));
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        log.setFinishedAt(finishedAt);
        log.setDurationMs(Duration.between(startedAt, finishedAt).toMillis());
        schedulerJobLogMapper.updateById(log);
    }

    private boolean tryClaimDueJob(SchedulerJob job, LocalDateTime now) {
        LocalDateTime lockUntil = now.plusSeconds(JOB_LOCK_SECONDS);
        return schedulerJobMapper.update(null, Wrappers.<SchedulerJob>update()
                .eq("id", job.getId())
                .in("status", List.of("ACTIVE", "ENABLED"))
                .and(wrapper -> wrapper.isNull("next_run_time")
                        .or()
                        .le("next_run_time", now))
                .and(wrapper -> wrapper.isNull("lock_until")
                        .or()
                        .le("lock_until", now))
                .set("status", canonicalStatus(job.getStatus()))
                .set("last_run_time", now)
                .set("next_run_time", now.plusMinutes(job.getIntervalMinutes() == null ? 1 : job.getIntervalMinutes()))
                .set("lock_owner", currentLockOwner())
                .set("lock_until", lockUntil)
                .set("updated_at", now)) > 0;
    }

    private void releaseDueJobLock(SchedulerJob job) {
        schedulerJobMapper.update(null, Wrappers.<SchedulerJob>update()
                .eq("id", job.getId())
                .eq("lock_owner", currentLockOwner())
                .set("lock_owner", null)
                .set("lock_until", null)
                .set("updated_at", LocalDateTime.now()));
    }

    private boolean tryClaimQueuedLog(SchedulerJobLog log, LocalDateTime startedAt) {
        return schedulerJobLogMapper.update(null, Wrappers.<SchedulerJobLog>update()
                .eq("id", log.getId())
                .eq("status", "QUEUED")
                .and(wrapper -> wrapper.isNull("next_retry_time")
                        .or()
                        .le("next_retry_time", startedAt))
                .set("status", "RUNNING")
                .set("error_message", null)
                .set("started_at", startedAt)
                .set("finished_at", null)
                .set("duration_ms", null)) > 0;
    }

    private int executeSupportedJob(SchedulerJob job, IntegrationProviderConfig provider) {
        if (provider != null && "DOUYIN_LAIKE".equalsIgnoreCase(provider.getProviderCode())) {
            return douyinClueSyncService.syncIncremental(provider);
        }
        if ("DOUYIN_CLUE_INCREMENTAL".equals(job.getJobCode())) {
            return douyinClueSyncService.syncIncremental();
        }
        String moduleCode = normalize(job.getModuleCode());
        String jobCode = normalize(job.getJobCode());
        if ("DISTRIBUTION".equals(moduleCode)) {
            return executeDistributionJob(jobCode);
        }
        throw new BusinessException("unsupported scheduler job");
    }

    private int executeDistributionJob(String jobCode) {
        if (JOB_DISTRIBUTION_OUTBOX_PROCESS.equals(jobCode)) {
            return schedulerOutboxService.processDue(20).size();
        }
        if (JOB_DISTRIBUTION_EXCEPTION_RETRY.equals(jobCode)) {
            return distributionExceptionRetryService.processRetryQueue(10).size();
        }
        throw new BusinessException("unsupported distribution scheduler job");
    }

    private boolean hasPendingLog(String jobCode) {
        Long count = schedulerJobLogMapper.selectCount(Wrappers.<SchedulerJobLog>lambdaQuery()
                .eq(SchedulerJobLog::getJobCode, normalize(jobCode))
                .in(SchedulerJobLog::getStatus, List.of("QUEUED", "RUNNING")));
        return count != null && count > 0;
    }

    private boolean exceedsRetryLimit(SchedulerJobLog log, SchedulerJob job) {
        int retryLimit = job.getRetryLimit() == null ? 3 : job.getRetryLimit();
        int retryCount = log.getRetryCount() == null ? 0 : log.getRetryCount();
        return retryCount >= retryLimit;
    }

    private SchedulerJob getJobOrThrow(String jobCode) {
        String normalizedJobCode = normalize(jobCode);
        SchedulerJob job = schedulerJobMapper.selectOne(Wrappers.<SchedulerJob>lambdaQuery()
                .eq(SchedulerJob::getJobCode, normalizedJobCode)
                .last("LIMIT 1"));
        if (job == null) {
            throw new BusinessException("scheduler job not found");
        }
        return job;
    }

    private void apply(SchedulerJob job, SchedulerJobUpsertRequest request) {
        job.setModuleCode(normalize(request.getModuleCode()));
        job.setSyncMode(StringUtils.hasText(request.getSyncMode()) ? normalize(request.getSyncMode()) : "INCREMENTAL");
        job.setIntervalMinutes(request.getIntervalMinutes() == null || request.getIntervalMinutes() <= 0 ? 1 : request.getIntervalMinutes());
        job.setRetryLimit(request.getRetryLimit() == null || request.getRetryLimit() < 0 ? 3 : request.getRetryLimit());
        job.setQueueName(StringUtils.hasText(request.getQueueName()) ? request.getQueueName().trim() : "default");
        job.setProviderId(request.getProviderId());
        job.setEndpoint(StringUtils.hasText(request.getEndpoint()) ? request.getEndpoint().trim() : null);
        job.setStatus(canonicalStatus(request.getStatus()));
    }

    private void validateJobScope(SchedulerJobUpsertRequest request) {
        String moduleCode = normalize(request.getModuleCode());
        String syncMode = StringUtils.hasText(request.getSyncMode()) ? normalize(request.getSyncMode()) : "INCREMENTAL";
        String jobCode = normalize(request.getJobCode());
        if ("CLUE".equals(moduleCode)) {
            if (!"INCREMENTAL".equals(syncMode) && !"MANUAL".equals(syncMode)) {
                throw new BusinessException("Scheduler V1 only supports incremental or manual Clue sync");
            }
            return;
        }
        if ("DISTRIBUTION".equals(moduleCode)) {
            if (!DISTRIBUTION_JOB_CODES.contains(jobCode)) {
                throw new BusinessException("unsupported distribution scheduler job");
            }
            if (!"INCREMENTAL".equals(syncMode) && !"MANUAL".equals(syncMode)) {
                throw new BusinessException("Scheduler V1 only supports incremental or manual distribution jobs");
            }
            return;
        }
        throw new BusinessException("Scheduler V1 only supports Clue intake and Distribution queue jobs");
    }

    private void audit(String jobCode,
                       Long logId,
                       String actionType,
                       PermissionRequestContext context,
                       String status,
                       String summary,
                       String detail) {
        SchedulerJobAuditLog auditLog = new SchedulerJobAuditLog();
        auditLog.setJobCode(normalize(jobCode));
        auditLog.setLogId(logId);
        auditLog.setActionType(actionType);
        auditLog.setActorType(context == null ? "SYSTEM" : "USER");
        auditLog.setActorUserId(context == null ? null : context.getCurrentUserId());
        auditLog.setActorRoleCode(context == null ? "SYSTEM" : normalize(context.getRoleCode()));
        auditLog.setStatus(status);
        auditLog.setSummary(summary);
        auditLog.setDetail(trimDetail(detail));
        auditLog.setCreatedAt(LocalDateTime.now());
        schedulerJobAuditLogMapper.insert(auditLog);
    }

    private String describeJob(SchedulerJob job) {
        if (job == null) {
            return null;
        }
        return "module=" + job.getModuleCode()
                + ",syncMode=" + job.getSyncMode()
                + ",intervalMinutes=" + job.getIntervalMinutes()
                + ",retryLimit=" + job.getRetryLimit()
                + ",queueName=" + job.getQueueName()
                + ",providerId=" + job.getProviderId()
                + ",status=" + job.getStatus();
    }

    private String trimDetail(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private String currentLockOwner() {
        return "seedcrm-scheduler-" + Integer.toHexString(System.identityHashCode(this));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isEnabled(String status) {
        return "ENABLED".equals(canonicalStatus(status));
    }

    private String canonicalStatus(String status) {
        String normalized = normalize(status);
        if (!StringUtils.hasText(normalized)) {
            return "ENABLED";
        }
        return switch (normalized) {
            case "ACTIVE", "ENABLED" -> "ENABLED";
            case "INACTIVE", "DISABLED" -> "DISABLED";
            default -> normalized;
        };
    }
}
