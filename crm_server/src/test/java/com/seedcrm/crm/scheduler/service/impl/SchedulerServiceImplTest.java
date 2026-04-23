package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.scheduler.dto.SchedulerJobUpsertRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerTriggerRequest;
import com.seedcrm.crm.scheduler.entity.SchedulerJob;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobLogMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerJobMapper;
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

    private SchedulerServiceImpl schedulerService;

    @BeforeEach
    void setUp() {
        schedulerService = new SchedulerServiceImpl(schedulerJobMapper, schedulerJobLogMapper);
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
        request.setModuleCode("scheduler");

        SchedulerJob job = schedulerService.saveJob(request);

        assertThat(job.getJobCode()).isEqualTo("DOUYIN_CLUE_SYNC");
        assertThat(job.getIntervalMinutes()).isEqualTo(1);
        assertThat(job.getRetryLimit()).isEqualTo(3);
        assertThat(job.getStatus()).isEqualTo("ENABLED");
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

        SchedulerTriggerRequest request = new SchedulerTriggerRequest();
        request.setJobCode("douyin_clue_sync");
        request.setPayload("{\"cursor\":1}");

        SchedulerJobLog log = schedulerService.trigger(request);

        assertThat(log.getStatus()).isEqualTo("QUEUED");
        assertThat(log.getPayload()).contains("cursor");
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

        List<SchedulerJobLog> logs = schedulerService.retryFailed("douyin_clue_sync");

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("QUEUED");
        assertThat(logs.get(0).getRetryCount()).isEqualTo(2);
    }
}
