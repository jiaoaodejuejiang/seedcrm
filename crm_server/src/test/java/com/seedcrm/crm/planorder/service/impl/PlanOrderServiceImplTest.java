package com.seedcrm.crm.planorder.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.entity.OrderActionRecord;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderActionRecordMapper;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.service.OrderSettlementService;
import com.seedcrm.crm.planorder.dto.PlanOrderActionDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderAssignRoleDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderCreateDTO;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import com.seedcrm.crm.scheduler.service.SchedulerOutboxService;
import com.seedcrm.crm.systemflow.support.SystemFlowRuntimeBridge;
import com.seedcrm.crm.wecom.service.WecomTouchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private WecomTouchService wecomTouchService;

    @Mock
    private OrderActionRecordMapper orderActionRecordMapper;

    @Mock
    private DbLockService dbLockService;

    @Mock
    private SchedulerOutboxService schedulerOutboxService;

    @Mock
    private SystemFlowRuntimeBridge systemFlowRuntimeBridge;

    private PlanOrderServiceImpl planOrderService;

    @BeforeEach
    void setUp() {
        planOrderService = new PlanOrderServiceImpl(planOrderMapper, orderMapper, orderRoleRecordService,
                orderSettlementService, wecomTouchService, orderActionRecordMapper, dbLockService,
                schedulerOutboxService, systemFlowRuntimeBridge, new ObjectMapper());
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
    void createPlanOrderShouldBindCurrentStoreRoleWhenContextProvided() {
        Order order = new Order();
        order.setId(16L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        when(orderMapper.selectById(16L)).thenReturn(order);
        when(planOrderMapper.selectCount(any())).thenReturn(0L);
        when(planOrderMapper.insert(any(PlanOrder.class))).thenAnswer(invocation -> {
            PlanOrder created = invocation.getArgument(0);
            created.setId(88L);
            return 1;
        });

        PlanOrderCreateDTO dto = new PlanOrderCreateDTO();
        dto.setOrderId(16L);

        PlanOrder created = planOrderService.createPlanOrder(dto, 1001L, "STORE_SERVICE");

        assertThat(created.getId()).isEqualTo(88L);
        verify(orderRoleRecordService, times(1)).assignRole(88L, "STORE_SERVICE", 1001L);
    }

    @Test
    void startShouldRequireArriveBeforeServicing() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(1L);
        planOrder.setOrderId(10L);
        planOrder.setStatus(PlanOrderStatus.ARRIVED.name());
        when(planOrderMapper.selectById(1L)).thenReturn(planOrder);
        Order order = new Order();
        order.setId(10L);
        order.setVerificationStatus("VERIFIED");
        when(orderMapper.selectById(10L)).thenReturn(order);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(1L);

        assertThatThrownBy(() -> planOrderService.start(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must arrive before start");
    }

    @Test
    void confirmServiceFormShouldMarkPaperConfirmationAndRecordAction() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(50L);
        planOrder.setOrderId(500L);
        planOrder.setStatus(PlanOrderStatus.ARRIVED.name());
        when(planOrderMapper.selectById(50L)).thenReturn(planOrder);

        Order order = new Order();
        order.setId(500L);
        order.setStatus(OrderStatus.ARRIVED.name());
        order.setVerificationStatus("VERIFIED");
        order.setServiceDetailJson("{\"serviceRequirement\":\"paper form\"}");
        when(dbLockService.lockOrder(500L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(50L);

        PlanOrder confirmed = planOrderService.confirmServiceForm(dto, 9001L, "STORE_SERVICE");

        assertThat(confirmed).isSameAs(planOrder);
        assertThat(order.getServiceDetailJson()).contains(
                "\"serviceFormStatus\":\"PRINT_CONFIRMED\"",
                "\"signatureMode\":\"PAPER\"",
                "\"confirmedByUserId\":9001",
                "\"confirmedByRoleCode\":\"STORE_SERVICE\"");
        ArgumentCaptor<OrderActionRecord> recordCaptor = ArgumentCaptor.forClass(OrderActionRecord.class);
        verify(orderActionRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getActionType()).isEqualTo("SERVICE_FORM_CONFIRM");
    }

    @Test
    void startShouldRejectWhenServiceFormNotConfirmed() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(51L);
        planOrder.setOrderId(510L);
        planOrder.setStatus(PlanOrderStatus.ARRIVED.name());
        planOrder.setArriveTime(java.time.LocalDateTime.now().minusMinutes(20));
        when(planOrderMapper.selectById(51L)).thenReturn(planOrder);

        Order order = new Order();
        order.setId(510L);
        order.setVerificationStatus("VERIFIED");
        order.setServiceDetailJson("{\"serviceRequirement\":\"saved only\"}");
        when(orderMapper.selectById(510L)).thenReturn(order);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(51L);

        assertThatThrownBy(() -> planOrderService.start(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("printed and confirmed");
        verify(planOrderMapper, never()).updateById(any(PlanOrder.class));
    }

    @Test
    void startShouldAllowConfirmedPaperServiceForm() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(52L);
        planOrder.setOrderId(520L);
        planOrder.setStatus(PlanOrderStatus.ARRIVED.name());
        planOrder.setArriveTime(java.time.LocalDateTime.now().minusMinutes(20));
        when(planOrderMapper.selectById(52L)).thenReturn(planOrder);
        when(planOrderMapper.updateById(any(PlanOrder.class))).thenReturn(1);

        Order order = new Order();
        order.setId(520L);
        order.setStatus(OrderStatus.ARRIVED.name());
        order.setVerificationStatus("VERIFIED");
        order.setServiceDetailJson("{\"serviceFormStatus\":\"PRINT_CONFIRMED\"}");
        when(orderMapper.selectById(520L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(52L);

        PlanOrder started = planOrderService.start(dto);

        assertThat(started.getStatus()).isEqualTo(PlanOrderStatus.SERVICING.name());
        assertThat(started.getStartTime()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SERVING.name());
    }

    @Test
    void arriveShouldRejectWhenOrderNotVerified() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(41L);
        planOrder.setOrderId(410L);
        planOrder.setStatus(PlanOrderStatus.ARRIVED.name());
        when(planOrderMapper.selectById(41L)).thenReturn(planOrder);

        Order order = new Order();
        order.setId(410L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setVerificationStatus(null);
        when(orderMapper.selectById(410L)).thenReturn(order);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(41L);

        assertThatThrownBy(() -> planOrderService.arrive(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("verified before arrive");
    }

    @Test
    void finishShouldRejectWhenOrderNotVerified() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(42L);
        planOrder.setOrderId(420L);
        planOrder.setStatus(PlanOrderStatus.SERVICING.name());
        planOrder.setArriveTime(java.time.LocalDateTime.now().minusHours(1));
        planOrder.setStartTime(java.time.LocalDateTime.now().minusMinutes(30));
        when(planOrderMapper.selectById(42L)).thenReturn(planOrder);

        Order order = new Order();
        order.setId(420L);
        order.setStatus(OrderStatus.SERVING.name());
        order.setVerificationStatus("");
        when(dbLockService.lockOrder(420L)).thenReturn(order);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(42L);

        assertThatThrownBy(() -> planOrderService.finish(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("verified before finish");
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
        order.setVerificationStatus("VERIFIED");
        order.setExternalPartnerCode("DISTRIBUTION");
        order.setExternalOrderId("o_20001");
        order.setServiceDetailJson("{\"serviceRequirement\":\"confirmed offline paper form\"}");
        when(dbLockService.lockOrder(30L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        when(schedulerOutboxService.enqueueFulfillmentEvent(order, planOrder, "crm.order.used"))
                .thenReturn(new SchedulerOutboxEvent());

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(3L);

        PlanOrder finished = planOrderService.finish(dto);

        assertThat(finished.getStatus()).isEqualTo(PlanOrderStatus.FINISHED.name());
        assertThat(finished.getFinishTime()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED.name());
        assertThat(order.getCompleteTime()).isNotNull();
        verify(orderActionRecordMapper).insert(any(OrderActionRecord.class));
        verify(orderSettlementService).settleCompletedOrder(30L);
        verify(schedulerOutboxService).enqueueFulfillmentEvent(order, planOrder, "crm.order.used");
    }

    @Test
    void finishShouldFailWhenDistributionOutboxCannotBeEnqueued() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(33L);
        planOrder.setOrderId(330L);
        planOrder.setStatus(PlanOrderStatus.SERVICING.name());
        planOrder.setArriveTime(java.time.LocalDateTime.now().minusHours(1));
        planOrder.setStartTime(java.time.LocalDateTime.now().minusMinutes(30));
        when(planOrderMapper.selectById(33L)).thenReturn(planOrder);
        when(planOrderMapper.updateById(any(PlanOrder.class))).thenReturn(1);

        Order order = new Order();
        order.setId(330L);
        order.setStatus(OrderStatus.SERVING.name());
        order.setVerificationStatus("VERIFIED");
        order.setExternalPartnerCode("DISTRIBUTION");
        order.setExternalOrderId("dist_order_330");
        order.setServiceDetailJson("{\"serviceRequirement\":\"confirmed offline paper form\"}");
        when(dbLockService.lockOrder(330L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        when(schedulerOutboxService.enqueueFulfillmentEvent(order, planOrder, "crm.order.used"))
                .thenThrow(new BusinessException("failed to enqueue outbox event"));

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(33L);

        assertThatThrownBy(() -> planOrderService.finish(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("failed to enqueue outbox event");
        verify(orderSettlementService).settleCompletedOrder(330L);
        verify(schedulerOutboxService).enqueueFulfillmentEvent(order, planOrder, "crm.order.used");
    }

    @Test
    void finishShouldFailWhenDistributionOutboxReturnsNull() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(34L);
        planOrder.setOrderId(340L);
        planOrder.setStatus(PlanOrderStatus.SERVICING.name());
        planOrder.setArriveTime(java.time.LocalDateTime.now().minusHours(1));
        planOrder.setStartTime(java.time.LocalDateTime.now().minusMinutes(30));
        when(planOrderMapper.selectById(34L)).thenReturn(planOrder);
        when(planOrderMapper.updateById(any(PlanOrder.class))).thenReturn(1);

        Order order = new Order();
        order.setId(340L);
        order.setStatus(OrderStatus.SERVING.name());
        order.setSource("distribution");
        order.setVerificationStatus("VERIFIED");
        order.setServiceDetailJson("{\"serviceRequirement\":\"confirmed offline paper form\"}");
        when(dbLockService.lockOrder(340L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        when(schedulerOutboxService.enqueueFulfillmentEvent(order, planOrder, "crm.order.used")).thenReturn(null);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(34L);

        assertThatThrownBy(() -> planOrderService.finish(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("distribution fulfillment outbox event is required");
        verify(orderSettlementService).settleCompletedOrder(340L);
        verify(schedulerOutboxService).enqueueFulfillmentEvent(order, planOrder, "crm.order.used");
    }

    @Test
    void finishShouldRejectWhenServiceDetailNotSaved() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(31L);
        planOrder.setOrderId(310L);
        planOrder.setStatus(PlanOrderStatus.SERVICING.name());
        planOrder.setArriveTime(java.time.LocalDateTime.now().minusHours(1));
        planOrder.setStartTime(java.time.LocalDateTime.now().minusMinutes(30));
        when(planOrderMapper.selectById(31L)).thenReturn(planOrder);

        Order order = new Order();
        order.setId(310L);
        order.setStatus(OrderStatus.SERVING.name());
        order.setVerificationStatus("VERIFIED");
        order.setServiceDetailJson("");
        when(dbLockService.lockOrder(310L)).thenReturn(order);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(31L);

        assertThatThrownBy(() -> planOrderService.finish(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("service form must be saved");
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
        order.setVerificationStatus("VERIFIED");
        when(orderMapper.selectById(40L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        PlanOrderActionDTO dto = new PlanOrderActionDTO();
        dto.setPlanOrderId(4L);

        PlanOrder arrived = planOrderService.arrive(dto);

        assertThat(arrived.getArriveTime()).isNotNull();
        assertThat(order.getArriveTime()).isNotNull();
    }
}
