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
}
