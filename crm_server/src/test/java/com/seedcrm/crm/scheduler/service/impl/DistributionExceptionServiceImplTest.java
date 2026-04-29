package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventRequest;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionMemberPayload;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionOrderPayload;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import com.seedcrm.crm.scheduler.mapper.DistributionExceptionRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributionExceptionServiceImplTest {

    @Mock
    private DistributionExceptionRecordMapper exceptionRecordMapper;

    private DistributionExceptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DistributionExceptionServiceImpl(exceptionRecordMapper, new ObjectMapper());
    }

    @Test
    void shouldRecordDistributionIngestFailure() {
        when(exceptionRecordMapper.selectOne(any())).thenReturn(null);
        when(exceptionRecordMapper.insert(any(DistributionExceptionRecord.class))).thenReturn(1);

        service.recordFailure("distribution", event(), "{\"order\":{\"externalOrderId\":\"o_raw\"}}",
                "trace-001", "idem-001", "INGEST_FAILED", "external order does not exist");

        ArgumentCaptor<DistributionExceptionRecord> captor = ArgumentCaptor.forClass(DistributionExceptionRecord.class);
        verify(exceptionRecordMapper).insert(captor.capture());
        DistributionExceptionRecord record = captor.getValue();
        assertThat(record.getPartnerCode()).isEqualTo("DISTRIBUTION");
        assertThat(record.getEventType()).isEqualTo("distribution.order.refund_pending");
        assertThat(record.getEventId()).isEqualTo("evt_refund_001");
        assertThat(record.getIdempotencyKey()).isEqualTo("idem-001");
        assertThat(record.getExternalOrderId()).isEqualTo("o_20001");
        assertThat(record.getExternalMemberId()).isEqualTo("m_10001");
        assertThat(record.getPhone()).isEqualTo("13800000000");
        assertThat(record.getHandlingStatus()).isEqualTo("OPEN");
        assertThat(record.getRetryCount()).isZero();
    }

    @Test
    void shouldMarkExceptionHandledWithOperatorContext() {
        DistributionExceptionRecord record = new DistributionExceptionRecord();
        record.setId(5L);
        record.setHandlingStatus("OPEN");
        when(exceptionRecordMapper.selectById(5L)).thenReturn(record, record);
        when(exceptionRecordMapper.updateById(any(DistributionExceptionRecord.class))).thenReturn(1);

        DistributionExceptionRecord handled = service.markHandled(5L, context(), "manual repaired");

        assertThat(handled.getHandlingStatus()).isEqualTo("HANDLED");
        assertThat(handled.getHandlerUserId()).isEqualTo(9001L);
        assertThat(handled.getHandlerRoleCode()).isEqualTo("INTEGRATION_OPERATOR");
        assertThat(handled.getHandleRemark()).isEqualTo("manual repaired");
        assertThat(handled.getHandledAt()).isNotNull();
    }

    private DistributionEventRequest event() {
        DistributionMemberPayload member = new DistributionMemberPayload();
        member.setExternalMemberId("m_10001");
        member.setPhone("13800000000");
        DistributionOrderPayload order = new DistributionOrderPayload();
        order.setExternalOrderId("o_20001");
        DistributionEventRequest event = new DistributionEventRequest();
        event.setPartnerCode("DISTRIBUTION");
        event.setEventType("distribution.order.refund_pending");
        event.setEventId("evt_refund_001");
        event.setMember(member);
        event.setOrder(order);
        return event;
    }

    private PermissionRequestContext context() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setCurrentUserId(9001L);
        context.setRoleCode("integration_operator");
        return context;
    }
}
