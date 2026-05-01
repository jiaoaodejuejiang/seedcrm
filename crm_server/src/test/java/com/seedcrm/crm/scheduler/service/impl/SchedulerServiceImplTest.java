package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.service.DouyinClueSyncService;
import com.seedcrm.crm.scheduler.dto.SchedulerJobUpsertRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerTriggerRequest;
import com.seedcrm.crm.scheduler.dto.DistributionReconciliationDtos.DistributionReconciliationResult;
import com.seedcrm.crm.scheduler.entity.SchedulerJob;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobAuditLogMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobLogMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobMapper;
import com.seedcrm.crm.scheduler.service.DistributionExceptionRetryService;
import com.seedcrm.crm.scheduler.service.DistributionReconciliationService;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import com.seedcrm.crm.scheduler.service.SchedulerOutboxService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulerServiceImplTest {

    @Mock
    private SchedulerJobMapper schedulerJobMapper;

    @Mock
    private SchedulerJobLogMapper schedulerJobLogMapper;

    @Mock
    private SchedulerJobAuditLogMapper schedulerJobAuditLogMapper;

    @Mock
    private DouyinClueSyncService douyinClueSyncService;

    @Mock
    private SchedulerIntegrationService schedulerIntegrationService;

    @Mock
    private SchedulerOutboxService schedulerOutboxService;

    @Mock
    private DistributionExceptionRetryService distributionExceptionRetryService;

    @Mock
    private DistributionReconciliationService distributionReconciliationService;

    private SchedulerServiceImpl schedulerService;

    @BeforeEach
    void setUp() {
        schedulerService = new SchedulerServiceImpl(
                schedulerJobMapper, schedulerJobLogMapper, schedulerJobAuditLogMapper, douyinClueSyncService,
                schedulerIntegrationService, schedulerOutboxService, distributionExceptionRetryService,
                distributionReconciliationService);
    }

    @Test
    void shouldSaveSchedulerJobWithDefaults() {
        when(schedulerJobMapper.selectOne(any())).thenReturn(null);
        when(schedulerJobMapper.insert(org.mockito.ArgumentMatchers.<SchedulerJob>any())).thenAnswer(invocation -> {
            SchedulerJob job = invocation.getArgument(0);
            job.setId(1L);
            return 1;
        });

        SchedulerJobUpsertRequest request = new SchedulerJobUpsertRequest();
        request.setJobCode("douyin_clue_sync");
        request.setModuleCode("clue");
        request.setProviderId(88L);

        SchedulerJob job = schedulerService.saveJob(request, null);

        assertThat(job.getJobCode()).isEqualTo("DOUYIN_CLUE_SYNC");
        assertThat(job.getIntervalMinutes()).isEqualTo(1);
        assertThat(job.getRetryLimit()).isEqualTo(3);
        assertThat(job.getStatus()).isEqualTo("ENABLED");
        assertThat(job.getProviderId()).isEqualTo(88L);
    }

    @Test
    void shouldRejectUnsupportedSchedulerJobInV1() {
        SchedulerJobUpsertRequest request = new SchedulerJobUpsertRequest();
        request.setJobCode("order_sync");
        request.setModuleCode("order");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> schedulerService.saveJob(request, null))
                .hasMessageContaining("only supports Clue intake and Distribution queue jobs");
    }

    @Test
    void shouldSaveDistributionOutboxSchedulerJob() {
        when(schedulerJobMapper.selectOne(any())).thenReturn(null);
        when(schedulerJobMapper.insert(org.mockito.ArgumentMatchers.<SchedulerJob>any())).thenAnswer(invocation -> {
            SchedulerJob job = invocation.getArgument(0);
            job.setId(11L);
            return 1;
        });

        SchedulerJobUpsertRequest request = new SchedulerJobUpsertRequest();
        request.setJobCode("distribution_outbox_process");
        request.setModuleCode("distribution");
        request.setSyncMode("incremental");
        request.setQueueName("distribution-outbox");

        SchedulerJob job = schedulerService.saveJob(request, null);

        assertThat(job.getJobCode()).isEqualTo("DISTRIBUTION_OUTBOX_PROCESS");
        assertThat(job.getModuleCode()).isEqualTo("DISTRIBUTION");
        assertThat(job.getQueueName()).isEqualTo("distribution-outbox");
    }

    @Test
    void shouldCreateQueuedLogWhenTriggeringJob() {
        SchedulerJob job = new SchedulerJob();
        job.setId(1L);
        job.setJobCode("DOUYIN_CLUE_SYNC");
        job.setStatus("ENABLED");
        job.setIntervalMinutes(1);
        when(schedulerJobMapper.selectOne(any())).thenReturn(job);
        when(schedulerJobLogMapper.insert(org.mockito.ArgumentMatchers.<SchedulerJobLog>any())).thenAnswer(invocation -> {
            SchedulerJobLog log = invocation.getArgument(0);
            log.setId(10L);
            return 1;
        });
        when(schedulerJobLogMapper.selectById(10L)).thenAnswer(invocation -> {
            SchedulerJobLog log = new SchedulerJobLog();
            log.setId(10L);
            log.setJobCode("DOUYIN_CLUE_SYNC");
            log.setStatus("QUEUED");
            log.setPayload("{\"cursor\":1}");
            return log;
        });

        SchedulerTriggerRequest request = new SchedulerTriggerRequest();
        request.setJobCode("douyin_clue_sync");
        request.setPayload("{\"cursor\":1}");

        SchedulerJobLog log = schedulerService.trigger(request, null);

        assertThat(log.getStatus()).isEqualTo("QUEUED");
        assertThat(log.getPayload()).contains("cursor");
    }

    @Test
    void shouldRecordQueueMetadataWhenTriggeringJob() {
        SchedulerJob job = new SchedulerJob();
        job.setId(1L);
        job.setJobCode("DOUYIN_CLUE_SYNC");
        job.setStatus("ENABLED");
        job.setIntervalMinutes(1);
        job.setQueueName("douyin-clue-sync");
        job.setProviderId(88L);
        job.setSyncMode("INCREMENTAL");
        when(schedulerJobMapper.selectOne(any())).thenReturn(job);
        when(schedulerJobLogMapper.insert(org.mockito.ArgumentMatchers.<SchedulerJobLog>any())).thenAnswer(invocation -> {
            SchedulerJobLog log = invocation.getArgument(0);
            log.setId(10L);
            return 1;
        });
        when(schedulerJobLogMapper.selectById(10L)).thenAnswer(invocation -> {
            SchedulerJobLog log = new SchedulerJobLog();
            log.setId(10L);
            log.setJobCode("DOUYIN_CLUE_SYNC");
            log.setQueueName("douyin-clue-sync");
            log.setProviderId(88L);
            log.setSyncMode("INCREMENTAL");
            log.setTriggerType("MANUAL");
            log.setStatus("QUEUED");
            return log;
        });

        SchedulerTriggerRequest request = new SchedulerTriggerRequest();
        request.setJobCode("douyin_clue_sync");

        SchedulerJobLog log = schedulerService.trigger(request, null);

        assertThat(log.getQueueName()).isEqualTo("douyin-clue-sync");
        assertThat(log.getProviderId()).isEqualTo(88L);
        assertThat(log.getSyncMode()).isEqualTo("INCREMENTAL");
        assertThat(log.getTriggerType()).isEqualTo("MANUAL");
    }

    @Test
    void shouldRetryFailedLogsWithinRetryLimit() {
        SchedulerJob job = new SchedulerJob();
        job.setId(1L);
        job.setJobCode("DOUYIN_CLUE_SYNC");
        job.setStatus("ENABLED");
        job.setRetryLimit(3);
        when(schedulerJobMapper.selectOne(any())).thenReturn(job);

        SchedulerJobLog failedLog = new SchedulerJobLog();
        failedLog.setId(100L);
        failedLog.setJobCode("DOUYIN_CLUE_SYNC");
        failedLog.setStatus("FAILED");
        failedLog.setRetryCount(1);
        when(schedulerJobLogMapper.selectList(any())).thenReturn(List.of(failedLog));

        List<SchedulerJobLog> logs = schedulerService.retryFailed("douyin_clue_sync", null);

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("QUEUED");
        assertThat(logs.get(0).getRetryCount()).isEqualTo(2);
        assertThat(logs.get(0).getNextRetryTime()).isNull();
        assertThat(logs.get(0).getTriggerType()).isEqualTo("MANUAL_RETRY");
    }

    @Test
    void shouldAutomaticallyRequeueDueFailedLogsWithinRetryLimit() {
        SchedulerJob job = new SchedulerJob();
        job.setId(1L);
        job.setJobCode("DOUYIN_CLUE_SYNC");
        job.setRetryLimit(3);

        SchedulerJobLog failedLog = new SchedulerJobLog();
        failedLog.setId(100L);
        failedLog.setJobCode("DOUYIN_CLUE_SYNC");
        failedLog.setStatus("FAILED");
        failedLog.setRetryCount(1);
        failedLog.setNextRetryTime(LocalDateTime.now().minusSeconds(1));

        when(schedulerJobLogMapper.selectList(any())).thenReturn(List.of(failedLog));
        when(schedulerJobMapper.selectOne(any())).thenReturn(job);

        schedulerService.requeueDueFailedLogs();

        assertThat(failedLog.getStatus()).isEqualTo("QUEUED");
        assertThat(failedLog.getRetryCount()).isEqualTo(2);
        assertThat(failedLog.getTriggerType()).isEqualTo("RETRY");
        assertThat(failedLog.getNextRetryTime()).isNull();
        verify(schedulerJobLogMapper).updateById(failedLog);
    }

    @Test
    void shouldRetrySingleFailedLog() {
        SchedulerJob job = new SchedulerJob();
        job.setId(1L);
        job.setJobCode("DOUYIN_CLUE_SYNC");
        job.setRetryLimit(3);

        SchedulerJobLog failedLog = new SchedulerJobLog();
        failedLog.setId(100L);
        failedLog.setJobCode("DOUYIN_CLUE_SYNC");
        failedLog.setStatus("FAILED");
        failedLog.setRetryCount(1);
        failedLog.setNextRetryTime(LocalDateTime.now().plusMinutes(1));

        when(schedulerJobLogMapper.selectById(100L)).thenReturn(failedLog);
        when(schedulerJobMapper.selectOne(any())).thenReturn(job);

        SchedulerJobLog retriedLog = schedulerService.retryLog(100L, null);

        assertThat(retriedLog.getStatus()).isEqualTo("QUEUED");
        assertThat(retriedLog.getRetryCount()).isEqualTo(2);
        assertThat(retriedLog.getTriggerType()).isEqualTo("MANUAL_RETRY");
        assertThat(retriedLog.getNextRetryTime()).isNull();
        verify(schedulerJobLogMapper).updateById(failedLog);
    }

    @Test
    void shouldDispatchActiveJobsAndNormalizeStatus() {
        SchedulerJob activeJob = new SchedulerJob();
        activeJob.setId(1L);
        activeJob.setJobCode("DOUYIN_CLUE_INCREMENTAL");
        activeJob.setStatus("ACTIVE");
        activeJob.setIntervalMinutes(1);

        when(schedulerJobMapper.selectList(any())).thenReturn(List.of(activeJob));
        when(schedulerJobLogMapper.selectCount(any())).thenReturn(0L);
        when(schedulerJobMapper.update(any(), any())).thenReturn(1);
        when(schedulerJobLogMapper.insert(org.mockito.ArgumentMatchers.<SchedulerJobLog>any())).thenReturn(1);

        schedulerService.dispatchDueJobs();

        assertThat(activeJob.getStatus()).isEqualTo("ENABLED");
        verify(schedulerJobMapper, org.mockito.Mockito.times(2)).update(any(), any());
        verify(schedulerJobLogMapper).insert(any(SchedulerJobLog.class));
    }

    @Test
    void shouldExecuteDistributionOutboxJobFromQueue() {
        SchedulerJob job = new SchedulerJob();
        job.setId(2L);
        job.setJobCode("DISTRIBUTION_OUTBOX_PROCESS");
        job.setModuleCode("DISTRIBUTION");
        job.setStatus("ENABLED");
        job.setRetryLimit(3);
        SchedulerJobLog queuedLog = new SchedulerJobLog();
        queuedLog.setId(200L);
        queuedLog.setJobCode("DISTRIBUTION_OUTBOX_PROCESS");
        queuedLog.setStatus("QUEUED");
        queuedLog.setRetryCount(0);

        when(schedulerJobLogMapper.selectList(any())).thenReturn(List.of(queuedLog));
        when(schedulerJobMapper.selectOne(any())).thenReturn(job);
        when(schedulerJobLogMapper.update(any(), any())).thenReturn(1);
        when(schedulerOutboxService.processDue(20)).thenReturn(List.of(new com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent()));

        schedulerService.processQueuedLogs();

        assertThat(queuedLog.getStatus()).isEqualTo("SUCCESS");
        assertThat(queuedLog.getImportedCount()).isEqualTo(1);
        assertThat(queuedLog.getPayload()).contains("\"processedCount\":1");
        verify(schedulerOutboxService).processDue(20);
        verify(schedulerJobLogMapper).updateById(queuedLog);
    }

    @Test
    void shouldExecuteDistributionExceptionRetryJobFromQueue() {
        SchedulerJob job = new SchedulerJob();
        job.setId(3L);
        job.setJobCode("DISTRIBUTION_EXCEPTION_RETRY");
        job.setModuleCode("DISTRIBUTION");
        job.setStatus("ENABLED");
        job.setRetryLimit(3);
        SchedulerJobLog queuedLog = new SchedulerJobLog();
        queuedLog.setId(300L);
        queuedLog.setJobCode("DISTRIBUTION_EXCEPTION_RETRY");
        queuedLog.setStatus("QUEUED");
        queuedLog.setRetryCount(0);

        when(schedulerJobLogMapper.selectList(any())).thenReturn(List.of(queuedLog));
        when(schedulerJobMapper.selectOne(any())).thenReturn(job);
        when(schedulerJobLogMapper.update(any(), any())).thenReturn(1);
        when(distributionExceptionRetryService.processRetryQueue(10))
                .thenReturn(List.of(new com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord()));

        schedulerService.processQueuedLogs();

        assertThat(queuedLog.getStatus()).isEqualTo("SUCCESS");
        assertThat(queuedLog.getImportedCount()).isEqualTo(1);
        assertThat(queuedLog.getPayload()).contains("\"processedCount\":1");
        verify(distributionExceptionRetryService).processRetryQueue(10);
        verify(schedulerJobLogMapper).updateById(queuedLog);
    }

    @Test
    void shouldExecuteDistributionStatusCheckJobFromQueue() {
        SchedulerJob job = new SchedulerJob();
        job.setId(4L);
        job.setJobCode("DISTRIBUTION_STATUS_CHECK");
        job.setModuleCode("DISTRIBUTION");
        job.setStatus("ENABLED");
        job.setRetryLimit(3);
        SchedulerJobLog queuedLog = new SchedulerJobLog();
        queuedLog.setId(400L);
        queuedLog.setJobCode("DISTRIBUTION_STATUS_CHECK");
        queuedLog.setStatus("QUEUED");
        queuedLog.setRetryCount(0);

        when(schedulerJobLogMapper.selectList(any())).thenReturn(List.of(queuedLog));
        when(schedulerJobMapper.selectOne(any())).thenReturn(job);
        when(schedulerJobLogMapper.update(any(), any())).thenReturn(1);
        when(distributionReconciliationService.checkOrderStatus(20))
                .thenReturn(List.of(reconciliationResult("dist_order_001", "REPLAYED", "SUCCESS")));

        schedulerService.processQueuedLogs();

        assertThat(queuedLog.getStatus()).isEqualTo("SUCCESS");
        assertThat(queuedLog.getImportedCount()).isEqualTo(1);
        assertThat(queuedLog.getPayload()).contains(
                "\"replayed\":1",
                "\"orderId\":\"901\"",
                "\"externalOrderId\":\"dist_order_001\"",
                "\"partnerCode\":\"DISTRIBUTION\"",
                "\"processStatus\":\"SUCCESS\"");
        verify(distributionReconciliationService).checkOrderStatus(20);
        verify(schedulerJobLogMapper).updateById(queuedLog);
    }

    @Test
    void shouldExecuteDistributionReconcilePullJobFromQueue() {
        SchedulerJob job = new SchedulerJob();
        job.setId(5L);
        job.setJobCode("DISTRIBUTION_RECONCILE_PULL");
        job.setModuleCode("DISTRIBUTION");
        job.setStatus("ENABLED");
        job.setRetryLimit(3);
        SchedulerJobLog queuedLog = new SchedulerJobLog();
        queuedLog.setId(500L);
        queuedLog.setJobCode("DISTRIBUTION_RECONCILE_PULL");
        queuedLog.setStatus("QUEUED");
        queuedLog.setRetryCount(0);

        when(schedulerJobLogMapper.selectList(any())).thenReturn(List.of(queuedLog));
        when(schedulerJobMapper.selectOne(any())).thenReturn(job);
        when(schedulerJobLogMapper.update(any(), any())).thenReturn(1);
        when(distributionReconciliationService.pullReconciliation(20))
                .thenReturn(List.of(
                        reconciliationResult("dist_order_001", "NO_CHANGE", "SUCCESS"),
                        reconciliationResult("dist_order_002", "FAILED", "FAILED")));

        schedulerService.processQueuedLogs();

        assertThat(queuedLog.getStatus()).isEqualTo("SUCCESS");
        assertThat(queuedLog.getImportedCount()).isEqualTo(2);
        assertThat(queuedLog.getPayload()).contains(
                "\"noChange\":1",
                "\"failed\":1",
                "\"orderId\":\"901\"",
                "\"externalOrderId\":\"dist_order_002\"");
        verify(distributionReconciliationService).pullReconciliation(20);
        verify(schedulerJobLogMapper).updateById(queuedLog);
    }

    @Test
    void shouldDryRunDistributionStatusCheckWithoutQueueingOrWritingCoreTables() {
        SchedulerJob job = new SchedulerJob();
        job.setId(6L);
        job.setJobCode("DISTRIBUTION_STATUS_CHECK");
        job.setModuleCode("DISTRIBUTION");
        job.setStatus("ENABLED");
        job.setQueueName("distribution-status-check");
        when(schedulerJobMapper.selectOne(any())).thenReturn(job);
        when(distributionReconciliationService.dryRunOrderStatus(20))
                .thenReturn(List.of(reconciliationResult("dist_order_001", "WOULD_REPLAY", "PRECHECK")));

        SchedulerTriggerRequest request = new SchedulerTriggerRequest();
        request.setJobCode("DISTRIBUTION_STATUS_CHECK");

        SchedulerJobLog preview = schedulerService.dryRun(request, null);

        assertThat(preview.getStatus()).isEqualTo("PRECHECK");
        assertThat(preview.getTriggerType()).isEqualTo("DRY_RUN");
        assertThat(preview.getPayload()).contains(
                "\"source\":\"dry-run\"",
                "\"willWriteCoreTables\":false",
                "\"wouldReplay\":1");
        verify(schedulerJobLogMapper, never()).insert(any(SchedulerJobLog.class));
        verify(distributionReconciliationService).dryRunOrderStatus(20);
        verify(distributionReconciliationService, never()).checkOrderStatus(20);
    }

    private DistributionReconciliationResult reconciliationResult(String externalOrderId, String action, String status) {
        DistributionReconciliationResult result = new DistributionReconciliationResult();
        result.setOrderId(901L);
        result.setExternalOrderId(externalOrderId);
        result.setPartnerCode("DISTRIBUTION");
        result.setAction(action);
        result.setStatus(status);
        result.setProcessStatus(status);
        result.setEventType("distribution.order.refunded");
        result.setIdempotencyKey("DISTRIBUTION:STATUS_CHECK:distribution.order.refunded:" + externalOrderId);
        result.setMessage("processed " + externalOrderId);
        return result;
    }
}
