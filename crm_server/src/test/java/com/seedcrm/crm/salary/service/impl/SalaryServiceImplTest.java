package com.seedcrm.crm.salary.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.planorder.mapper.OrderRoleRecordMapper;
import com.seedcrm.crm.salary.dto.SalaryStatResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalaryServiceImplTest {

    @Mock
    private OrderRoleRecordMapper orderRoleRecordMapper;

    private SalaryServiceImpl salaryService;

    @BeforeEach
    void setUp() {
        salaryService = new SalaryServiceImpl(orderRoleRecordMapper);
    }

    @Test
    void statShouldAggregateOrderRoleAndServiceCounts() {
        when(orderRoleRecordMapper.countDistinctPlanOrdersByUserId(9L)).thenReturn(3L);
        when(orderRoleRecordMapper.countFinishedServicesByUserId(9L)).thenReturn(2L);
        when(orderRoleRecordMapper.selectRoleDistributionByUserId(9L)).thenReturn(List.of(
                Map.of("roleCode", "CONSULTANT", "roleCount", 2L),
                Map.of("roleCode", "DOCTOR", "roleCount", 1L)
        ));

        SalaryStatResponse response = salaryService.stat(9L);

        assertThat(response.getParticipateOrderCount()).isEqualTo(3L);
        assertThat(response.getServiceCount()).isEqualTo(2L);
        assertThat(response.getRoleDistribution()).containsEntry("CONSULTANT", 2L);
        assertThat(response.getRoleDistribution()).containsEntry("DOCTOR", 1L);
    }

    @Test
    void statShouldRejectInvalidUserId() {
        assertThatThrownBy(() -> salaryService.stat(0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("userId is required");
    }
}
