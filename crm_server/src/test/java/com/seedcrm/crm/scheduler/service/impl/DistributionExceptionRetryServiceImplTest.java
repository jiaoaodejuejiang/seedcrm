package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventResponse;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import com.seedcrm.crm.scheduler.mapper.DistributionExceptionRecordMapper;
import com.seedcrm.crm.scheduler.service.DistributionEventIngestService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributionExceptionRetryServiceImplTest {

    @Mock
    private DistributionExceptionRecordMapper exceptionRecordMapper;

    @Mock
    private DistributionEventIngestService distributionEventIngestService;

    private DistributionExceptionRetryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DistributionExceptionRetryServiceImpl(
                exceptionRecordMapper,
                distributionEventIngestService,
                new ObjectMapper());
    }

    @Test
    void shouldReplayQueuedExceptionThroughIngestServiceAndMarkHandled() {
        DistributionExceptionRecord record = retryRecord();
        DistributionEventResponse response = new DistributionEventResponse();
        response.setTraceId("trace-replay-001");
        response.setProcessStatus("SUCCESS");
        response.setIdempotencyResult("CREATED");
        when(exceptionRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(exceptionRecordMapper.update(any(), any())).thenReturn(1);
        when(exceptionRecordMapper.selectById(7L)).thenReturn(record, record);
        when(distributionEventIngestService.replayFromScheduler(any(JsonNode.class), any(), any())).thenReturn(response);

        List<DistributionExceptionRecord> processed = service.processRetryQueue(10);

        assertThat(processed).hasSize(1);
        ArgumentCaptor<DistributionExceptionRecord> captor = ArgumentCaptor.forClass(DistributionExceptionRecord.class);
        verify(exceptionRecordMapper).updateById(captor.capture());
        assertThat(captor.getValue().getHandlingStatus()).isEqualTo("HANDLED");
        assertThat(captor.getValue().getHandleRemark()).contains("trace-replay-001");
        verify(distributionEventIngestService).replayFromScheduler(any(JsonNode.class), org.mockito.ArgumentMatchers.eq("DISTRIBUTION"),
                org.mockito.ArgumentMatchers.eq("idem-retry-001"));
    }

    @Test
    void shouldReturnToOpenWhenReplayFails() {
        DistributionExceptionRecord record = retryRecord();
        when(exceptionRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(exceptionRecordMapper.update(any(), any())).thenReturn(1);
        when(exceptionRecordMapper.selectById(7L)).thenReturn(record, record, record);
        when(distributionEventIngestService.replayFromScheduler(any(JsonNode.class), any(), any()))
                .thenThrow(new com.seedcrm.crm.common.exception.BusinessException("external order does not exist"));

        List<DistributionExceptionRecord> processed = service.processRetryQueue(10);

        assertThat(processed).hasSize(1);
        ArgumentCaptor<DistributionExceptionRecord> captor = ArgumentCaptor.forClass(DistributionExceptionRecord.class);
        verify(exceptionRecordMapper).updateById(captor.capture());
        assertThat(captor.getValue().getHandlingStatus()).isEqualTo("OPEN");
        assertThat(captor.getValue().getErrorMessage()).contains("external order does not exist");
        assertThat(captor.getValue().getHandleRemark()).contains("manual review");
    }

    @Test
    void shouldReturnToOpenWhenReplayStillQueuesException() {
        DistributionExceptionRecord record = retryRecord();
        DistributionEventResponse response = new DistributionEventResponse();
        response.setTraceId("trace-replay-conflict");
        response.setProcessStatus("SUCCESS");
        response.setIdempotencyResult("EXCEPTION_QUEUED");
        response.setMessage("duplicate external order conflict queued for manual handling");
        when(exceptionRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(exceptionRecordMapper.update(any(), any())).thenReturn(1);
        when(exceptionRecordMapper.selectById(7L)).thenReturn(record, record, record);
        when(distributionEventIngestService.replayFromScheduler(any(JsonNode.class), any(), any())).thenReturn(response);

        List<DistributionExceptionRecord> processed = service.processRetryQueue(10);

        assertThat(processed).hasSize(1);
        ArgumentCaptor<DistributionExceptionRecord> captor = ArgumentCaptor.forClass(DistributionExceptionRecord.class);
        verify(exceptionRecordMapper).updateById(captor.capture());
        assertThat(captor.getValue().getHandlingStatus()).isEqualTo("OPEN");
        assertThat(captor.getValue().getErrorMessage()).contains("duplicate external order conflict");
        assertThat(captor.getValue().getHandleRemark()).contains("manual review");
    }

    private DistributionExceptionRecord retryRecord() {
        DistributionExceptionRecord record = new DistributionExceptionRecord();
        record.setId(7L);
        record.setPartnerCode("DISTRIBUTION");
        record.setIdempotencyKey("idem-retry-001");
        record.setHandlingStatus("RETRY_QUEUED");
        record.setRawPayload("""
                {
                  "eventType": "distribution.order.paid",
                  "eventId": "evt_retry_001",
                  "partnerCode": "DISTRIBUTION",
                  "member": {
                    "externalMemberId": "m_10001",
                    "name": "Zhang San",
                    "phone": "13800000000",
                    "role": "member"
                  },
                  "order": {
                    "externalOrderId": "o_20001",
                    "type": "coupon",
                    "amount": 19900,
                    "paidAt": "2026-04-29T09:58:00+08:00",
                    "status": "paid"
                  },
                  "rawData": {}
                }
                """);
        return record;
    }
}
