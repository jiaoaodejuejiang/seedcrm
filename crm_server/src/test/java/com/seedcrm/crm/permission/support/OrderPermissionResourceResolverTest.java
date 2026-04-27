package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.mapper.OrderRoleRecordMapper;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderPermissionResourceResolverTest {

    @Mock
    private ClueMapper clueMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PlanOrderMapper planOrderMapper;

    @Mock
    private OrderRoleRecordMapper orderRoleRecordMapper;

    private OrderPermissionResourceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OrderPermissionResourceResolver(clueMapper, orderMapper, planOrderMapper, orderRoleRecordMapper);
    }

    @Test
    void shouldPreferClueOwnerWhenOrderIsBoundToClue() {
        Order order = new Order();
        order.setId(21L);
        order.setClueId(7L);
        when(orderMapper.selectById(21L)).thenReturn(order);

        Clue clue = new Clue();
        clue.setId(7L);
        clue.setCurrentOwnerId(5001L);
        when(clueMapper.selectById(7L)).thenReturn(clue);

        assertThat(resolver.resolveOrderOwnerId(21L)).isEqualTo(5001L);
    }

    @Test
    void shouldFallbackToCurrentServiceRoleWhenOrderHasNoClueOwnerForStoreScope() {
        Order order = new Order();
        order.setId(23L);
        order.setClueId(null);
        when(orderMapper.selectById(23L)).thenReturn(order);

        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(13L);
        planOrder.setOrderId(23L);
        when(planOrderMapper.selectOne(any())).thenReturn(planOrder);

        OrderRoleRecord financeRecord = new OrderRoleRecord();
        financeRecord.setPlanOrderId(13L);
        financeRecord.setRoleCode("FIN_ROLE_1776832364");
        financeRecord.setUserId(91001L);
        financeRecord.setIsCurrent(1);
        OrderRoleRecord serviceRecord = new OrderRoleRecord();
        serviceRecord.setPlanOrderId(13L);
        serviceRecord.setRoleCode("STORE_SERVICE");
        serviceRecord.setUserId(5101L);
        serviceRecord.setIsCurrent(1);
        when(orderRoleRecordMapper.selectList(any())).thenReturn(List.of(financeRecord, serviceRecord));

        assertThat(resolver.resolveOrderOwnerId(23L)).isNull();
        assertThat(resolver.resolveOrderStoreScopeOwnerId(23L)).isEqualTo(5101L);
    }

    @Test
    void shouldPreferCurrentServiceRoleOverClueOwnerForStoreScope() {
        Order order = new Order();
        order.setId(24L);
        order.setClueId(9L);
        when(orderMapper.selectById(24L)).thenReturn(order);

        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(14L);
        planOrder.setOrderId(24L);
        when(planOrderMapper.selectOne(any())).thenReturn(planOrder);

        OrderRoleRecord serviceRecord = new OrderRoleRecord();
        serviceRecord.setPlanOrderId(14L);
        serviceRecord.setRoleCode("STORE_SERVICE");
        serviceRecord.setUserId(5101L);
        serviceRecord.setIsCurrent(1);
        when(orderRoleRecordMapper.selectList(any())).thenReturn(List.of(serviceRecord));

        assertThat(resolver.resolveOrderStoreScopeOwnerId(24L)).isEqualTo(5101L);
    }
}
