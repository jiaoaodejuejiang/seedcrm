package com.seedcrm.crm.clue.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.entity.ClueRecord;
import com.seedcrm.crm.clue.mapper.ClueRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClueRecordServiceImplTest {

    @Mock
    private ClueRecordMapper clueRecordMapper;

    private ClueRecordServiceImpl clueRecordService;

    @BeforeEach
    void setUp() {
        clueRecordService = new ClueRecordServiceImpl(clueRecordMapper);
    }

    @Test
    void addRecordIfAbsentShouldInsertNewRecord() {
        ClueRecord record = new ClueRecord();
        record.setClueId(1L);
        record.setRecordKey("douyin:100");
        record.setTitle("订单同步");

        boolean created = clueRecordService.addRecordIfAbsent(record);

        assertThat(created).isTrue();
        assertThat(record.getRecordType()).isEqualTo("CLUE");
        assertThat(record.getOccurredAt()).isNotNull();
        verify(clueRecordMapper).insert(record);
    }

    @Test
    void addRecordIfAbsentShouldSkipExistingRecordKey() {
        when(clueRecordMapper.selectOne(any())).thenReturn(new ClueRecord());
        ClueRecord record = new ClueRecord();
        record.setClueId(1L);
        record.setRecordKey("douyin:100");

        boolean created = clueRecordService.addRecordIfAbsent(record);

        assertThat(created).isFalse();
        verify(clueRecordMapper, never()).insert(any(ClueRecord.class));
    }

    @Test
    void addRecordIfAbsentShouldTrimFieldsToDatabaseLimits() {
        ClueRecord record = new ClueRecord();
        record.setClueId(1L);
        record.setRecordKey("douyin:order:" + "x".repeat(180));
        record.setRecordType("ORDER_ACTION_STATUS_THAT_IS_TOO_LONG");
        record.setSourceChannel("DOUYIN_SOURCE_CHANNEL_THAT_IS_TOO_LONG");
        record.setExternalRecordId("r".repeat(160));
        record.setExternalOrderId("o".repeat(160));
        record.setTitle("订单同步".repeat(80));
        record.setContent("内容".repeat(400));

        boolean created = clueRecordService.addRecordIfAbsent(record);

        assertThat(created).isTrue();
        assertThat(record.getRecordKey()).hasSizeLessThanOrEqualTo(128);
        assertThat(record.getRecordType()).hasSizeLessThanOrEqualTo(32);
        assertThat(record.getSourceChannel()).hasSizeLessThanOrEqualTo(32);
        assertThat(record.getExternalRecordId()).hasSizeLessThanOrEqualTo(128);
        assertThat(record.getExternalOrderId()).hasSizeLessThanOrEqualTo(128);
        assertThat(record.getTitle()).hasSizeLessThanOrEqualTo(128);
        assertThat(record.getContent()).hasSizeLessThanOrEqualTo(500);
        verify(clueRecordMapper).insert(record);
    }

    @Test
    void findClueIdByExternalIdentityShouldPreferExternalRecordIdThenOrderId() {
        ClueRecord matchedByRecord = new ClueRecord();
        matchedByRecord.setClueId(21L);
        when(clueRecordMapper.selectOne(any())).thenReturn(matchedByRecord);

        Long clueId = clueRecordService.findClueIdByExternalIdentity("DOUYIN", "clue-1", "order-1");

        assertThat(clueId).isEqualTo(21L);
    }

    @Test
    void findClueIdByExternalIdentityShouldFallbackToOrderId() {
        ClueRecord matchedByOrder = new ClueRecord();
        matchedByOrder.setClueId(22L);
        when(clueRecordMapper.selectOne(any())).thenReturn(null, matchedByOrder);

        Long clueId = clueRecordService.findClueIdByExternalIdentity("DOUYIN", "missing-record", "order-2");

        assertThat(clueId).isEqualTo(22L);
    }
}
