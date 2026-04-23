package com.seedcrm.crm.planorder.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.service.OrderSettlementService;
import com.seedcrm.crm.planorder.dto.PlanOrderActionDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderAssignRoleDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderCreateDTO;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanOrderServiceImplTest {

    @Mock
    private PlanOrderMapper planOrderMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderRoleRecordServiceImpl orderRoleRecordService;

    @Mock
    private OrderSettlementService orderSettlementService;

    private PlanOrderServiceImpl planOrderService;

    @BeforeEach
    void setUp() {
        planOrderService = new PlanOrderServiceImpl(planOrderMapper, orderMapper, orderRoleRecordService,
                orderSettlementService);
    }

    @Test
    void createPlanOrderShouldEnforceOneToOneOrderRelation() {
        Order order = new Order();
        order.setId(10L);
        order.setStatus(OrderStatus.PAID_DEPOSIT.name());
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(planOrderMapper.selectCount(any())).thenReturn(1L);

        PlanOrderCreateDTO dto = new PlanOrderCreateDTO();
        dto.setOrderId(10L);

        assertThatThrownBy(() -> planOrderService.createPlanOrder(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("plan order already exists");
        verify(planOrderMapper, never()).insert(any(PlanOrder.class));
    }

    @Test
    void createPlanOrderShouldRequirePaidOrder() {
        Order order = new Order();
        order.setId(15L);
        order.setStatus(OrderStatus.CREATED.name());
        when(orderMapper.selectById(15L)).thenReturn(order);

        PlanOrderCreateDTO dto = new PlanOrderCreateDTO();
        dto.setOrderId(15L);

        assertThatThrownBy(() -> planOrderService.createPlanOrder(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("order must be paid");
    }

    @Test
    void startShouldRequireArriveBeforeServicing() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(1L);
        planOrder.setOrderId(10L);
        planOrder.setStatus(PlanOrderStatus.ARRIVED.name());
        when(planOrderMapper.selectById(1L)).thenReturn(planOrder);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(1L);

        assertThatThrownBy(() -> planOrderService.start(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must arrive before start");
    }

    @Test
    void assignRoleShouldRejectFinishedPlanOrder() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(2L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        when(planOrderMapper.selectById(2L)).thenReturn(planOrder);

        PlanOrderAssignRoleDTO dto = new PlanOrderAssignRoleDTO();
        dto.setPlanOrderId(2L);
        dto.setRoleCode("DOCTOR");
        dto.setUserId(66L);

        assertThatThrownBy(() -> planOrderService.assignRole(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("finished");
    }

    @Test
    void finishShouldCompleteLinkedOrder() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(3L);
        planOrder.setOrderId(30L);
        planOrder.setStatus(PlanOrderStatus.SERVICING.name());
        planOrder.setArriveTime(java.time.LocalDateTime.now().minusHours(1));
        planOrder.setStartTime(java.time.LocalDateTime.now().minusMinutes(30));
        when(planOrderMapper.selectById(3L)).thenReturn(planOrder);
        when(planOrderMapper.updateById(any(PlanOrder.class))).thenReturn(1);

        Order order = new Order();
        order.setId(30L);
        order.setStatus(OrderStatus.CREATED.name());
        when(orderMapper.selectById(30L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(3L);

        PlanOrder finished = planOrderService.finish(dto);

        assertThat(finished.getStatus()).isEqualTo(PlanOrderStatus.FINISHED.name());
        assertThat(finished.getFinishTime()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED.name());
        assertThat(order.getCompleteTime()).isNotNull();
        verify(orderSettlementService).settleCompletedOrder(30L);
    }

    @Test
    void arriveShouldWriteOrderTraceTime() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(4L);
        planOrder.setOrderId(40L);
        planOrder.setStatus(PlanOrderStatus.ARRIVED.name());
        when(planOrderMapper.selectById(4L)).thenReturn(planOrder);
        when(planOrderMapper.updateById(any(PlanOrder.class))).thenReturn(1);

        Order order = new Order();
        order.setId(40L);
        order.setStatus(OrderStatus.CREATED.name());
        when(orderMapper.selectById(40L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(4L);

        PlanOrder arrived = planOrderService.arrive(dto);

        assertThat(arrived.getArriveTime()).isNotNull();
        assertThat(order.getArriveTime()).isNotNull();
    }
}
