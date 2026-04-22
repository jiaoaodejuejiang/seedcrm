package com.seedcrm.crm.planorder.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.mapper.OrderRoleRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderRoleRecordServiceImplTest {

    @Mock
    private OrderRoleRecordMapper orderRoleRecordMapper;

    private OrderRoleRecordServiceImpl orderRoleRecordService;

    @BeforeEach
    void setUp() {
        orderRoleRecordService = new OrderRoleRecordServiceImpl(orderRoleRecordMapper);
    }

    @Test
    void assignRoleShouldClosePreviousCurrentRecordAndCreateHistory() {
        OrderRoleRecord currentRecord = new OrderRoleRecord();
        currentRecord.setId(1L);
        currentRecord.setPlanOrderId(8L);
        currentRecord.setRoleCode("DOCTOR");
        currentRecord.setUserId(100L);
        currentRecord.setIsCurrent(1);
        when(orderRoleRecordMapper.selectOne(any())).thenReturn(currentRecord);
        when(orderRoleRecordMapper.updateById(any(OrderRoleRecord.class))).thenReturn(1);
        when(orderRoleRecordMapper.insert(any(OrderRoleRecord.class))).thenAnswer(invocation -> {
            OrderRoleRecord record = invocation.getArgument(0);
            record.setId(2L);
            return 1;
        });

        OrderRoleRecord record = orderRoleRecordService.assignRole(8L, "doctor", 200L);

        assertThat(currentRecord.getIsCurrent()).isEqualTo(0);
        assertThat(currentRecord.getEndTime()).isNotNull();
        assertThat(record.getId()).isEqualTo(2L);
        assertThat(record.getPlanOrderId()).isEqualTo(8L);
        assertThat(record.getRoleCode()).isEqualTo("DOCTOR");
        assertThat(record.getUserId()).isEqualTo(200L);
        assertThat(record.getIsCurrent()).isEqualTo(1);
    }

    @Test
    void assignRoleShouldRejectSameCurrentUser() {
        OrderRoleRecord currentRecord = new OrderRoleRecord();
        currentRecord.setId(1L);
        currentRecord.setPlanOrderId(8L);
        currentRecord.setRoleCode("CONSULTANT");
        currentRecord.setUserId(300L);
        currentRecord.setIsCurrent(1);
        when(orderRoleRecordMapper.selectOne(any())).thenReturn(currentRecord);

        assertThatThrownBy(() -> orderRoleRecordService.assignRole(8L, "CONSULTANT", 300L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already assigned");
        verify(orderRoleRecordMapper, never()).insert(any(OrderRoleRecord.class));
    }
}
