package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import com.seedcrm.crm.scheduler.mapper.DistributionExceptionRecordMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobLogMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerOutboxEventMapper;
import com.seedcrm.crm.scheduler.service.SchedulerIdempotencyHealthService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulerMonitorServiceImplTest {

    @Mock
    private SchedulerOutboxEventMapper schedulerOutboxEventMapper;

    @Mock
    private DistributionExceptionRecordMapper distributionExceptionRecordMapper;

    @Mock
    private SchedulerJobLogMapper schedulerJobLogMapper;

    @Mock
    private SchedulerIdempotencyHealthService schedulerIdempotencyHealthService;

    private SchedulerMonitorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchedulerMonitorServiceImpl(
                schedulerOutboxEventMapper,
                distributionExceptionRecordMapper,
                schedulerJobLogMapper,
                schedulerIdempotencyHealthService,
                new ObjectMapper());
    }

    @Test
    void shouldSummarizeDistributionOperationHealthWithoutWritingBusinessData() {
        SchedulerOutboxEvent latestOutbox = new SchedulerOutboxEvent();
        latestOutbox.setStatus("FAILED");
        latestOutbox.setUpdatedAt(LocalDateTime.now());
        when(schedulerOutboxEventMapper.selectCount(any()))
                .thenReturn(2L, 0L, 4L, 1L, 1L);
        when(schedulerOutboxEventMapper.selectList(any())).thenReturn(List.of(latestOutbox));

        DistributionExceptionRecord latestException = new DistributionExceptionRecord();
        latestException.setHandlingStatus("OPEN");
        latestException.setUpdatedAt(LocalDateTime.now());
        when(distributionExceptionRecordMapper.selectCount(any()))
                .thenReturn(3L, 1L, 9L);
        when(distributionExceptionRecordMapper.selectList(any())).thenReturn(List.of(latestException));

        SchedulerIdempotencyHealthResponse idempotency = new SchedulerIdempotencyHealthResponse();
        idempotency.setHealthy(false);
        idempotency.setStatus("DUPLICATE_DATA");
        idempotency.setDuplicateGroupCount(5L);
        idempotency.setAffectedLogCount(11L);
        when(schedulerIdempotencyHealthService.inspect("DISTRIBUTION")).thenReturn(idempotency);

        SchedulerJobLog latestFailed = jobLog(91L, "DISTRIBUTION_RECONCILE_PULL", "FAILED",
                "{\"processedCount\":2,\"actionCounts\":{\"replayed\":0,\"noChange\":1,\"failed\":1}}");
        SchedulerJobLog latestSuccess = jobLog(92L, "DISTRIBUTION_STATUS_CHECK", "SUCCESS",
                "{\"processedCount\":3,\"actionCounts\":{\"replayed\":1,\"noChange\":2,\"failed\":0}}");
        when(schedulerJobLogMapper.selectCount(any()))
                .thenReturn(10L, 7L, 2L, 1L, 0L);
        when(schedulerJobLogMapper.selectList(any()))
                .thenReturn(List.of(latestFailed), List.of(latestFailed, latestSuccess));

        var response = service.summarize("distribution");

        assertThat(response.getOverallStatus()).isEqualTo("ATTENTION");
        assertThat(response.getOutbox().getTotalAttention()).isEqualTo(4L);
        assertThat(response.getExceptions().getTotalAttention()).isEqualTo(4L);
        assertThat(response.getIdempotency().getStatus()).isEqualTo("DUPLICATE_DATA");
        assertThat(response.getJobs().getSuccessRate24h()).isEqualTo(70);
        assertThat(response.getRecentBatches()).hasSize(2);
        assertThat(response.getRecentBatches().get(0).getFailedCount()).isEqualTo(1);
        assertThat(response.getRecommendedActions())
                .anyMatch(action -> action.contains("履约回推"))
                .anyMatch(action -> action.contains("分销异常队列"))
                .anyMatch(action -> action.contains("幂等健康"));
    }

    private SchedulerJobLog jobLog(Long id, String jobCode, String status, String payload) {
        SchedulerJobLog log = new SchedulerJobLog();
        log.setId(id);
        log.setJobCode(jobCode);
        log.setTriggerType("AUTO");
        log.setStatus(status);
        log.setImportedCount(null);
        log.setPayload(payload);
        log.setCreatedAt(LocalDateTime.now());
        log.setStartedAt(LocalDateTime.now());
        log.setFinishedAt(LocalDateTime.now());
        return log;
    }
}
