package com.seedcrm.crm.scheduler.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.clue.service.DouyinClueSyncService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.scheduler.dto.SchedulerJobUpsertRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerTriggerRequest;
import com.seedcrm.crm.scheduler.entity.SchedulerJob;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobLogMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobMapper;
import com.seedcrm.crm.scheduler.service.SchedulerService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SchedulerServiceImpl implements SchedulerService {

    private final SchedulerJobMapper schedulerJobMapper;
    private final SchedulerJobLogMapper schedulerJobLogMapper;
    private final DouyinClueSyncService douyinClueSyncService;

    public SchedulerServiceImpl(SchedulerJobMapper schedulerJobMapper,
                                SchedulerJobLogMapper schedulerJobLogMapper,
                                DouyinClueSyncService douyinClueSyncService) {
        this.schedulerJobMapper = schedulerJobMapper;
        this.schedulerJobLogMapper = schedulerJobLogMapper;
        this.douyinClueSyncService = douyinClueSyncService;
    }

    @Override
    public List<SchedulerJob> listJobs() {
        return schedulerJobMapper.selectList(Wrappers.<SchedulerJob>lambdaQuery()
                .orderByAsc(SchedulerJob::getModuleCode)
                .orderByAsc(SchedulerJob::getJobCode)
                .orderByAsc(SchedulerJob::getId)).stream()
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
    public SchedulerJob saveJob(SchedulerJobUpsertRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getJobCode())
                || !StringUtils.hasText(request.getModuleCode())) {
            throw new BusinessException("jobCode and moduleCode are required");
        }
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
            return job;
        }
        apply(existing, request);
        existing.setUpdatedAt(now);
        if (schedulerJobMapper.updateById(existing) <= 0) {
            throw new BusinessException("failed to update scheduler job");
        }
        return existing;
    }

    @Override
    public SchedulerJobLog trigger(SchedulerTriggerRequest request) {
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
        SchedulerJobLog log = enqueue(job, payload, now);
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
            job.setStatus(canonicalStatus(job.getStatus()));
            job.setLastRunTime(now);
            job.setNextRunTime(now.plusMinutes(job.getIntervalMinutes() == null ? 1 : job.getIntervalMinutes()));
            job.setUpdatedAt(now);
            schedulerJobMapper.updateById(job);
            enqueue(job, "{\"source\":\"auto\"}", now);
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 8000)
    public void processQueuedLogs() {
        List<SchedulerJobLog> queuedLogs = schedulerJobLogMapper.selectList(Wrappers.<SchedulerJobLog>lambdaQuery()
                .eq(SchedulerJobLog::getStatus, "QUEUED")
                .orderByAsc(SchedulerJobLog::getCreatedAt)
                .orderByAsc(SchedulerJobLog::getId)
                .last("LIMIT 20"));
        for (SchedulerJobLog log : queuedLogs) {
            execute(log);
        }
    }

    @Override
    public List<SchedulerJobLog> retryFailed(String jobCode) {
        SchedulerJob job = getJobOrThrow(jobCode);
        List<SchedulerJobLog> failedLogs = schedulerJobLogMapper.selectList(Wrappers.<SchedulerJobLog>lambdaQuery()
                .eq(SchedulerJobLog::getJobCode, job.getJobCode())
                .eq(SchedulerJobLog::getStatus, "FAILED")
                .orderByAsc(SchedulerJobLog::getCreatedAt)
                .orderByAsc(SchedulerJobLog::getId));
        for (SchedulerJobLog log : failedLogs) {
            if (log.getRetryCount() != null && job.getRetryLimit() != null && log.getRetryCount() >= job.getRetryLimit()) {
                continue;
            }
            log.setRetryCount(log.getRetryCount() == null ? 1 : log.getRetryCount() + 1);
            log.setStatus("QUEUED");
            log.setErrorMessage(null);
            log.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
            schedulerJobLogMapper.updateById(log);
        }
        return listLogs(job.getJobCode());
    }

    private SchedulerJobLog enqueue(SchedulerJob job, String payload, LocalDateTime now) {
        SchedulerJobLog log = new SchedulerJobLog();
        log.setJobCode(job.getJobCode());
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
        log.setStatus("RUNNING");
        log.setErrorMessage(null);
        schedulerJobLogMapper.updateById(log);
        try {
            if ("DOUYIN_CLUE_INCREMENTAL".equals(job.getJobCode())) {
                int importedCount = douyinClueSyncService.syncIncremental();
                log.setStatus("SUCCESS");
                log.setPayload("{\"source\":\"sync\",\"importedCount\":" + importedCount + "}");
            } else {
                log.setStatus("FAILED");
                log.setErrorMessage("unsupported scheduler job");
            }
        } catch (Exception exception) {
            log.setStatus("FAILED");
            log.setErrorMessage(exception.getMessage());
            log.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
        }
        schedulerJobLogMapper.updateById(log);
    }

    private boolean hasPendingLog(String jobCode) {
        Long count = schedulerJobLogMapper.selectCount(Wrappers.<SchedulerJobLog>lambdaQuery()
                .eq(SchedulerJobLog::getJobCode, normalize(jobCode))
                .in(SchedulerJobLog::getStatus, List.of("QUEUED", "RUNNING")));
        return count != null && count > 0;
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
        job.setEndpoint(StringUtils.hasText(request.getEndpoint()) ? request.getEndpoint().trim() : null);
        job.setStatus(canonicalStatus(request.getStatus()));
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
