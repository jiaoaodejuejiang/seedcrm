package com.seedcrm.crm.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.service.CustomerService;
import com.seedcrm.crm.customer.service.CustomerTagService;
import com.seedcrm.crm.distributor.service.DistributorIncomeService;
import com.seedcrm.crm.order.dto.OrderActionDTO;
import com.seedcrm.crm.order.dto.OrderAppointmentDTO;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.dto.OrderPayDTO;
import com.seedcrm.crm.order.dto.OrderServiceDetailDTO;
import com.seedcrm.crm.order.dto.OrderVoucherVerifyDTO;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.entity.OrderActionRecord;
import com.seedcrm.crm.order.entity.OrderRefundRecord;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderActionRecordMapper;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.mapper.OrderRefundRecordMapper;
import com.seedcrm.crm.order.service.OrderSettlementService;
import com.seedcrm.crm.order.service.OrderVoucherVerificationGateway;
import com.seedcrm.crm.order.service.OrderVoucherVerificationResult;
import com.seedcrm.crm.order.support.ServiceFormVersionSupport;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import com.seedcrm.crm.systemflow.support.SystemFlowRuntimeBridge;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ClueMapper clueMapper;

    @Mock
    private CustomerService customerService;

    @Mock
    private CustomerTagService customerTagService;

    @Mock
    private PlanOrderMapper planOrderMapper;

    @Mock
    private DistributorIncomeService distributorIncomeService;

    @Mock
    private DbLockService dbLockService;

    @Mock
    private OrderSettlementService orderSettlementService;

    @Mock
    private OrderVoucherVerificationGateway voucherVerificationGateway;

    @Mock
    private OrderActionRecordMapper orderActionRecordMapper;

    @Mock
    private OrderRefundRecordMapper orderRefundRecordMapper;

    @Mock
    private SalaryDetailMapper salaryDetailMapper;

    @Mock
    private SystemFlowRuntimeBridge systemFlowRuntimeBridge;

    @Mock
    private SystemConfigService systemConfigService;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderMapper, clueMapper, customerService, customerTagService,
                planOrderMapper, distributorIncomeService, dbLockService, orderSettlementService,
                voucherVerificationGateway, orderActionRecordMapper, orderRefundRecordMapper, salaryDetailMapper,
                new ObjectMapper(), systemConfigService, systemFlowRuntimeBridge);
        lenient().when(systemConfigService.getBoolean("deposit.direct.enabled", true)).thenReturn(true);
        lenient().when(systemConfigService.getString("amount.visibility.service_confirm_edit_roles", "ADMIN,FINANCE,PHOTO_SELECTOR"))
                .thenReturn("ADMIN,FINANCE,PHOTO_SELECTOR");
        lenient().when(voucherVerificationGateway.verify(any(Order.class), any(), any()))
                .thenReturn(OrderVoucherVerificationResult.skipped());
    }

    @Test
    void createOrderShouldCreateCustomerFromClueWhenMissingCustomerId() {
        Clue clue = new Clue();
        clue.setId(100L);
        clue.setPhone("13800138000");
        clue.setName("Alice");
        clue.setWechat("alice-wechat");
        clue.setSourceChannel(SourceChannel.DISTRIBUTOR.name());
        clue.setSourceId(900L);
        when(clueMapper.selectById(100L)).thenReturn(clue);

        Customer customer = new Customer();
        customer.setId(200L);
        customer.setPhone("13800138000");
        when(customerService.getOrCreateByClue(clue)).thenReturn(customer);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return 1;
        });
        when(clueMapper.updateById(any(Clue.class))).thenReturn(1);

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setClueId(100L);
        dto.setType(1);
        dto.setAmount(new BigDecimal("888.00"));
        dto.setDeposit(new BigDecimal("88.00"));
        dto.setRemark("new order");

        Order order = orderService.createOrder(dto);

        assertThat(order.getId()).isEqualTo(1L);
        assertThat(order.getCustomerId()).isEqualTo(200L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID_DEPOSIT.name());
        assertThat(order.getSourceChannel()).isEqualTo(SourceChannel.DISTRIBUTOR.name());
        assertThat(order.getSourceId()).isEqualTo(900L);
        verify(customerService).getOrCreateByClue(clue);
        verify(customerService).refreshCustomerLifecycle(200L);
    }

    @Test
    void createOrderShouldKeepDistributionProductTypeWithoutDefaultDeposit() {
        Customer customer = new Customer();
        customer.setId(301L);
        when(customerService.getByIdOrThrow(301L)).thenReturn(customer);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(11L);
            return 1;
        });

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setCustomerId(301L);
        dto.setType(3);
        dto.setAmount(new BigDecimal("1280.00"));

        Order order = orderService.createOrder(dto);

        assertThat(order.getType()).isEqualTo(3);
        assertThat(order.getDeposit()).isEqualByComparingTo("0");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED.name());
    }

    @Test
    void createOrderShouldRejectMismatchedClueAndCustomer() {
        Clue clue = new Clue();
        clue.setId(100L);
        clue.setPhone("13800138000");
        when(clueMapper.selectById(100L)).thenReturn(clue);

        Customer customer = new Customer();
        customer.setId(300L);
        customer.setPhone("13900139000");
        when(customerService.getByIdOrThrow(300L)).thenReturn(customer);

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setClueId(100L);
        dto.setCustomerId(300L);
        dto.setType(1);
        dto.setAmount(new BigDecimal("500.00"));

        assertThatThrownBy(() -> orderService.createOrder(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clue phone does not match customer phone");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void payDepositShouldBindCustomerAndRefreshLifecycle() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("ORD202604211234567890");
        order.setClueId(101L);
        order.setAmount(new BigDecimal("500.00"));
        order.setDeposit(new BigDecimal("50.00"));
        order.setStatus(OrderStatus.CREATED.name());
        when(orderMapper.selectById(1L)).thenReturn(order);

        Clue clue = new Clue();
        clue.setId(101L);
        clue.setPhone("13800138001");
        when(clueMapper.selectById(101L)).thenReturn(clue);

        Customer customer = new Customer();
        customer.setId(201L);
        customer.setPhone("13800138001");
        when(customerService.getOrCreateByClue(clue)).thenReturn(customer);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        when(clueMapper.updateById(any(Clue.class))).thenReturn(1);

        OrderPayDTO dto = new OrderPayDTO();
        dto.setOrderId(1L);
        dto.setDeposit(new BigDecimal("80.00"));

        Order updated = orderService.payDeposit(dto);

        assertThat(updated.getCustomerId()).isEqualTo(201L);
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID_DEPOSIT.name());
        verify(customerService).refreshCustomerLifecycle(201L);
    }

    @Test
    void appointmentShouldRecordHeadcountSlotsAndSourceSurface() throws Exception {
        Order order = new Order();
        order.setId(55L);
        order.setOrderNo("ORD202604211234567955");
        order.setCustomerId(255L);
        order.setStatus(OrderStatus.PAID_DEPOSIT.name());
        when(dbLockService.lockOrder(55L)).thenReturn(order);
        when(customerService.getByIdOrThrow(255L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderAppointmentDTO dto = new OrderAppointmentDTO();
        dto.setOrderId(55L);
        dto.setAppointmentTime(LocalDateTime.of(2026, 4, 26, 10, 0));
        dto.setAppointmentSlots(List.of(
                LocalDateTime.of(2026, 4, 26, 10, 0),
                LocalDateTime.of(2026, 4, 26, 11, 0)));
        dto.setHeadcount(2);
        dto.setStoreName("Store B");
        dto.setSourceSurface("CUSTOMER_SCHEDULE");
        dto.setRemark("two customers");

        Order updated = orderService.appointment(dto, 9001L, "CLUE_MANAGER");

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.APPOINTMENT.name());
        assertThat(updated.getAppointmentTime()).isEqualTo(LocalDateTime.of(2026, 4, 26, 10, 0));
        ArgumentCaptor<OrderActionRecord> recordCaptor = ArgumentCaptor.forClass(OrderActionRecord.class);
        verify(orderActionRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getActionType()).isEqualTo("APPOINTMENT_CREATE");
        JsonNode extra = new ObjectMapper().readTree(recordCaptor.getValue().getExtraJson());
        assertThat(extra.path("headcountBefore").asInt()).isZero();
        assertThat(extra.path("headcountAfter").asInt()).isEqualTo(2);
        assertThat(extra.path("slotCountAfter").asInt()).isEqualTo(2);
        assertThat(extra.path("appointmentSlotsAfter").get(0).asText()).isEqualTo("2026-04-26 10:00:00");
        assertThat(extra.path("appointmentSlotsAfter").get(1).asText()).isEqualTo("2026-04-26 11:00:00");
        assertThat(extra.path("operatorRoleCode").asText()).isEqualTo("CLUE_MANAGER");
        assertThat(extra.path("sourceSurface").asText()).isEqualTo("CUSTOMER_SCHEDULE");
    }

    @Test
    void appointmentShouldAllowRescheduleWhenAlreadyAppointment() {
        Order order = new Order();
        order.setId(5L);
        order.setOrderNo("ORD202604211234567895");
        order.setCustomerId(205L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setAppointmentTime(LocalDateTime.of(2026, 4, 25, 10, 0));
        when(dbLockService.lockOrder(5L)).thenReturn(order);
        when(customerService.getByIdOrThrow(205L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderAppointmentDTO dto = new OrderAppointmentDTO();
        dto.setOrderId(5L);
        dto.setAppointmentTime(LocalDateTime.of(2026, 4, 26, 11, 30));
        dto.setPreviousStoreName("Store A");
        dto.setStoreName("Store B");
        dto.setRemark("reschedule");

        Order updated = orderService.appointment(dto, 9001L, "CLUE_MANAGER");

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.APPOINTMENT.name());
        assertThat(updated.getAppointmentTime()).isEqualTo(LocalDateTime.of(2026, 4, 26, 11, 30));
        assertThat(updated.getAppointmentStoreName()).isEqualTo("Store B");
        verify(customerService).refreshCustomerLifecycle(205L);
        ArgumentCaptor<OrderActionRecord> recordCaptor = ArgumentCaptor.forClass(OrderActionRecord.class);
        verify(orderActionRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getActionType()).isEqualTo("APPOINTMENT_CHANGE");
        assertThat(recordCaptor.getValue().getOperatorUserId()).isEqualTo(9001L);
        assertThat(recordCaptor.getValue().getExtraJson()).contains(
                "appointmentTimeBefore",
                "2026-04-25 10:00",
                "appointmentTimeAfter",
                "2026-04-26 11:30",
                "storeNameBefore",
                "Store A",
                "storeNameAfter",
                "Store B");
    }
    @Test
    void appointmentShouldUseServerStoreSnapshotWhenRescheduling() throws Exception {
        Order order = new Order();
        order.setId(51L);
        order.setOrderNo("ORD202604211234567951");
        order.setCustomerId(251L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setAppointmentTime(LocalDateTime.of(2026, 4, 25, 10, 0));
        order.setAppointmentStoreName("Store A");
        when(dbLockService.lockOrder(51L)).thenReturn(order);
        when(customerService.getByIdOrThrow(251L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderAppointmentDTO dto = new OrderAppointmentDTO();
        dto.setOrderId(51L);
        dto.setAppointmentTime(LocalDateTime.of(2026, 4, 26, 11, 30));
        dto.setPreviousStoreName("Stale Browser Store");
        dto.setStoreName("Store B");

        Order updated = orderService.appointment(dto, 9001L, "CLUE_MANAGER");

        assertThat(updated.getAppointmentStoreName()).isEqualTo("Store B");
        ArgumentCaptor<OrderActionRecord> recordCaptor = ArgumentCaptor.forClass(OrderActionRecord.class);
        verify(orderActionRecordMapper).insert(recordCaptor.capture());
        JsonNode extra = new ObjectMapper().readTree(recordCaptor.getValue().getExtraJson());
        assertThat(extra.path("storeNameBefore").asText()).isEqualTo("Store A");
        assertThat(extra.path("storeNameAfter").asText()).isEqualTo("Store B");
    }

    @Test
    void appointmentShouldRejectOccupiedSlot() {
        Order order = new Order();
        order.setId(53L);
        order.setOrderNo("ORD202604211234567953");
        order.setCustomerId(253L);
        order.setStatus(OrderStatus.PAID_DEPOSIT.name());
        when(dbLockService.lockOrder(53L)).thenReturn(order);
        when(customerService.getByIdOrThrow(253L)).thenReturn(new Customer());
        when(orderMapper.selectCount(any())).thenReturn(1L);

        OrderAppointmentDTO dto = new OrderAppointmentDTO();
        dto.setOrderId(53L);
        dto.setAppointmentTime(LocalDateTime.of(2026, 4, 26, 11, 30));
        dto.setStoreName("Store B");

        assertThatThrownBy(() -> orderService.appointment(dto, 9001L, "CLUE_MANAGER"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("appointment slot already occupied");
        verify(orderMapper, never()).updateById(any(Order.class));
        verify(orderActionRecordMapper, never()).insert(any(OrderActionRecord.class));
    }

    @Test
    void appointmentShouldRequireStoreNameForFirstAppointment() {
        Order order = new Order();
        order.setId(54L);
        order.setOrderNo("ORD202604211234567954");
        order.setCustomerId(254L);
        order.setStatus(OrderStatus.PAID_DEPOSIT.name());
        when(dbLockService.lockOrder(54L)).thenReturn(order);
        when(customerService.getByIdOrThrow(254L)).thenReturn(new Customer());

        OrderAppointmentDTO dto = new OrderAppointmentDTO();
        dto.setOrderId(54L);
        dto.setAppointmentTime(LocalDateTime.of(2026, 4, 26, 11, 30));

        assertThatThrownBy(() -> orderService.appointment(dto, 9001L, "CLUE_MANAGER"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("appointment storeName is required");
        verify(orderMapper, never()).updateById(any(Order.class));
        verify(orderActionRecordMapper, never()).insert(any(OrderActionRecord.class));
    }

    @Test
    void cancelAppointmentShouldKeepStoreSnapshotInActionRecord() throws Exception {
        Order order = new Order();
        order.setId(52L);
        order.setOrderNo("ORD202604211234567952");
        order.setCustomerId(252L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setAppointmentTime(LocalDateTime.of(2026, 4, 25, 15, 0));
        order.setAppointmentStoreName("Store A");
        when(dbLockService.lockOrder(52L)).thenReturn(order);
        when(customerService.getByIdOrThrow(252L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderActionDTO dto = new OrderActionDTO();
        dto.setOrderId(52L);
        dto.setRemark("cancel appointment");

        Order updated = orderService.cancelAppointment(dto, 9001L, "CLUE_MANAGER");

        assertThat(updated.getAppointmentTime()).isNull();
        assertThat(updated.getAppointmentStoreName()).isNull();
        ArgumentCaptor<OrderActionRecord> recordCaptor = ArgumentCaptor.forClass(OrderActionRecord.class);
        verify(orderActionRecordMapper).insert(recordCaptor.capture());
        JsonNode extra = new ObjectMapper().readTree(recordCaptor.getValue().getExtraJson());
        assertThat(extra.path("appointmentTimeBefore").asText()).isEqualTo("2026-04-25 15:00");
        assertThat(extra.path("appointmentTimeAfter").isNull()).isTrue();
        assertThat(extra.path("storeNameBefore").asText()).isEqualTo("Store A");
        assertThat(extra.path("storeNameAfter").isNull()).isTrue();
    }

    @Test
    void cancelAppointmentShouldRevertOrderToPaidDeposit() {
        Order order = new Order();
        order.setId(7L);
        order.setOrderNo("ORD202604211234567897");
        order.setCustomerId(207L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setAppointmentTime(LocalDateTime.of(2026, 4, 25, 15, 0));
        when(dbLockService.lockOrder(7L)).thenReturn(order);
        when(customerService.getByIdOrThrow(207L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderActionDTO dto = new OrderActionDTO();
        dto.setOrderId(7L);
        dto.setRemark("cancel appointment");

        Order updated = orderService.cancelAppointment(dto);

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID_DEPOSIT.name());
        assertThat(updated.getAppointmentTime()).isNull();
        verify(customerService).refreshCustomerLifecycle(207L);
        ArgumentCaptor<OrderActionRecord> recordCaptor = ArgumentCaptor.forClass(OrderActionRecord.class);
        verify(orderActionRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getActionType()).isEqualTo("APPOINTMENT_CANCEL");
        assertThat(recordCaptor.getValue().getExtraJson()).contains("appointmentTimeBefore", "2026-04-25 15:00");
    }

    @Test
    void completeShouldRefreshCustomerLifecycle() {
        Order order = new Order();
        order.setId(2L);
        order.setOrderNo("ORD202604211234567891");
        order.setCustomerId(202L);
        order.setStatus(OrderStatus.SERVING.name());
        when(dbLockService.lockOrder(2L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        when(customerService.getByIdOrThrow(202L)).thenReturn(new Customer());
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(9L);
        planOrder.setOrderId(2L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        when(planOrderMapper.selectOne(any())).thenReturn(planOrder);
        when(orderSettlementService.settleCompletedOrder(2L)).thenReturn(order);

        OrderActionDTO dto = new OrderActionDTO();
        dto.setOrderId(2L);
        dto.setRemark("done");

        Order completedOrder = orderService.complete(dto);

        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED.name());
        assertThat(completedOrder.getCompleteTime()).isNotNull();
        verify(customerService, times(1)).getByIdOrThrow(202L);
        verify(customerService).refreshCustomerLifecycle(202L);
        verify(orderSettlementService).settleCompletedOrder(2L);
        verify(orderActionRecordMapper).insert(any(OrderActionRecord.class));
    }

    @Test
    void completeShouldRejectWhenPlanOrderNotFinished() {
        Order order = new Order();
        order.setId(3L);
        order.setOrderNo("ORD202604211234567892");
        order.setCustomerId(203L);
        order.setStatus(OrderStatus.SERVING.name());
        when(dbLockService.lockOrder(3L)).thenReturn(order);
        when(customerService.getByIdOrThrow(203L)).thenReturn(new Customer());
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(10L);
        planOrder.setOrderId(3L);
        planOrder.setStatus(PlanOrderStatus.SERVICING.name());
        when(planOrderMapper.selectOne(any())).thenReturn(planOrder);

        OrderActionDTO dto = new OrderActionDTO();
        dto.setOrderId(3L);

        assertThatThrownBy(() -> orderService.complete(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("plan order must be finished");
    }

    @Test
    void refundShouldOnlyRegisterActionRecordForCompletedOrder() {
        Order order = new Order();
        order.setId(12L);
        order.setOrderNo("ORD202604211234567912");
        order.setCustomerId(212L);
        order.setStatus(OrderStatus.COMPLETED.name());
        order.setAmount(new BigDecimal("1000.00"));
        order.setServiceDetailJson("{\"serviceConfirmAmount\":300.00}");
        when(dbLockService.lockOrder(12L)).thenReturn(order);
        when(customerService.getByIdOrThrow(212L)).thenReturn(new Customer());
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(120L);
        planOrder.setOrderId(12L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        when(planOrderMapper.selectOne(any())).thenReturn(planOrder);
        when(orderRefundRecordMapper.selectList(any())).thenReturn(List.of());
        when(orderRefundRecordMapper.insert(any(OrderRefundRecord.class))).thenReturn(1);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderActionDTO dto = new OrderActionDTO();
        dto.setOrderId(12L);
        dto.setServiceRefundAmount(new BigDecimal("100.00"));
        dto.setRemark("store refund register");

        Order refunded = orderService.refund(dto, 9002L);

        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.COMPLETED.name());
        verify(orderActionRecordMapper).insert(any(OrderActionRecord.class));
        verify(orderSettlementService, never()).settleCompletedOrder(12L);
    }

    @Test
    void refundShouldChangeUnfinishedOrderStatus() {
        Order order = new Order();
        order.setId(13L);
        order.setOrderNo("ORD202604211234567913");
        order.setCustomerId(213L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setVerificationStatus("VERIFIED");
        order.setDeposit(new BigDecimal("200.00"));
        when(dbLockService.lockOrder(13L)).thenReturn(order);
        when(customerService.getByIdOrThrow(213L)).thenReturn(new Customer());
        when(orderRefundRecordMapper.selectList(any())).thenReturn(List.of());
        when(orderRefundRecordMapper.insert(any(OrderRefundRecord.class))).thenReturn(1);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderActionDTO dto = new OrderActionDTO();
        dto.setOrderId(13L);
        dto.setRefundScene("FINANCE_VERIFIED_PAYMENT");
        dto.setRefundAmount(new BigDecimal("88.00"));
        dto.setRemark("before service refund register");

        Order refunded = orderService.refund(dto, 9002L);

        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED.name());
        verify(orderActionRecordMapper).insert(any(OrderActionRecord.class));
        verify(orderSettlementService, never()).settleCompletedOrder(13L);
    }

    @Test
    void updateServiceDetailShouldPersistServiceRequirement() {
        Order order = new Order();
        order.setId(6L);
        order.setCustomerId(206L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setVerificationStatus("VERIFIED");
        when(orderMapper.selectById(6L)).thenReturn(order);
        when(customerService.getByIdOrThrow(206L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderServiceDetailDTO dto = new OrderServiceDetailDTO();
        dto.setOrderId(6L);
        dto.setServiceRequirement("customer must complete consult before package confirmation");
        dto.setServiceDetailJson("{\"serviceRequirement\":\"customer must complete consult before package confirmation\"}");

        Order updated = orderService.updateServiceDetail(dto);

        assertThat(updated.getRemark()).isEqualTo("customer must complete consult before package confirmation");
        assertThat(updated.getServiceDetailJson()).isEqualTo("{\"serviceRequirement\":\"customer must complete consult before package confirmation\"}");
        verify(customerService).refreshCustomerLifecycle(206L);
    }

    @Test
    void updateServiceDetailShouldRestoreMaskedAmountsWhenRestrictedRoleSaves() throws Exception {
        Order order = new Order();
        order.setId(66L);
        order.setCustomerId(266L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setVerificationStatus("VERIFIED");
        order.setServiceDetailJson("{\"serviceRequirement\":\"old\",\"serviceConfirmAmount\":1288.00,\"serviceTemplate\":{\"config\":{\"price\":99}}}");
        when(orderMapper.selectById(66L)).thenReturn(order);
        when(customerService.getByIdOrThrow(266L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderServiceDetailDTO dto = new OrderServiceDetailDTO();
        dto.setOrderId(66L);
        dto.setServiceRequirement("updated");
        dto.setServiceDetailJson("{\"_amountsMasked\":true,\"serviceRequirement\":\"updated\",\"serviceConfirmAmount\":null,\"serviceTemplate\":{\"config\":{\"price\":null}}}");

        Order updated = orderService.updateServiceDetail(dto);

        JsonNode root = new ObjectMapper().readTree(updated.getServiceDetailJson());
        assertThat(root.has("_amountsMasked")).isFalse();
        assertThat(root.path("serviceRequirement").asText()).isEqualTo("updated");
        assertThat(root.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("1288.00");
        assertThat(root.path("serviceTemplate").path("config").path("price").asInt()).isEqualTo(99);
    }

    @Test
    void updateServiceDetailShouldPreserveAmountsWhenStoreManagerAttemptsAmountChange() throws Exception {
        Order order = new Order();
        order.setId(69L);
        order.setCustomerId(269L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setVerificationStatus("VERIFIED");
        order.setServiceDetailJson("{\"serviceRequirement\":\"old\",\"serviceConfirmAmount\":1288.00,\"serviceTemplate\":{\"config\":{\"price\":99}}}");
        when(orderMapper.selectById(69L)).thenReturn(order);
        when(customerService.getByIdOrThrow(269L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderServiceDetailDTO dto = new OrderServiceDetailDTO();
        dto.setOrderId(69L);
        dto.setServiceRequirement("updated");
        dto.setServiceDetailJson("{\"serviceRequirement\":\"updated\",\"serviceConfirmAmount\":2888.00,\"serviceTemplate\":{\"config\":{\"price\":199}}}");

        Order updated = orderService.updateServiceDetail(dto, "STORE_MANAGER");

        JsonNode root = new ObjectMapper().readTree(updated.getServiceDetailJson());
        assertThat(root.path("serviceRequirement").asText()).isEqualTo("updated");
        assertThat(root.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("1288.00");
        assertThat(root.path("serviceTemplate").path("config").path("price").asInt()).isEqualTo(99);
    }

    @Test
    void updateServiceDetailShouldAllowPhotoSelectorToChangeServiceConfirmAmount() throws Exception {
        Order order = new Order();
        order.setId(70L);
        order.setCustomerId(270L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setVerificationStatus("VERIFIED");
        order.setServiceDetailJson("{\"serviceRequirement\":\"old\",\"serviceConfirmAmount\":1288.00,\"serviceTemplate\":{\"config\":{\"price\":99}}}");
        when(orderMapper.selectById(70L)).thenReturn(order);
        when(customerService.getByIdOrThrow(270L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderServiceDetailDTO dto = new OrderServiceDetailDTO();
        dto.setOrderId(70L);
        dto.setServiceRequirement("updated");
        dto.setServiceDetailJson("{\"serviceRequirement\":\"updated\",\"serviceConfirmAmount\":2888.00,\"serviceTemplate\":{\"config\":{\"price\":199}}}");

        Order updated = orderService.updateServiceDetail(dto, "PHOTO_SELECTOR");

        JsonNode root = new ObjectMapper().readTree(updated.getServiceDetailJson());
        assertThat(root.path("serviceRequirement").asText()).isEqualTo("updated");
        assertThat(root.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("2888.00");
        assertThat(root.path("serviceTemplate").path("config").path("price").asInt()).isEqualTo(199);
    }

    @Test
    void updateServiceDetailShouldPreservePrintAuditWhenPrintableContentUnchanged() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String originalDetail = printedServiceDetail(mapper, "same content", true);
        Order order = new Order();
        order.setId(67L);
        order.setCustomerId(267L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setVerificationStatus("VERIFIED");
        order.setServiceDetailJson(originalDetail);
        when(orderMapper.selectById(67L)).thenReturn(order);
        when(customerService.getByIdOrThrow(267L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderServiceDetailDTO dto = new OrderServiceDetailDTO();
        dto.setOrderId(67L);
        dto.setServiceRequirement("same content");
        dto.setServiceDetailJson("{\"serviceRequirement\":\"same content\"}");

        Order updated = orderService.updateServiceDetail(dto);

        JsonNode root = mapper.readTree(updated.getServiceDetailJson());
        assertThat(root.path("printAudit").path("status").asText()).isEqualTo("PRINTED");
        assertThat(root.path("confirmation").path("status").asText()).isEqualTo("PRINT_CONFIRMED");
        assertThat(root.path("serviceFormStatus").asText()).isEqualTo("PRINT_CONFIRMED");
    }

    @Test
    void updateServiceDetailShouldExpirePrintAuditWhenPrintableContentChanged() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String originalDetail = printedServiceDetail(mapper, "old content", true);
        Order order = new Order();
        order.setId(68L);
        order.setCustomerId(268L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setVerificationStatus("VERIFIED");
        order.setServiceDetailJson(originalDetail);
        when(orderMapper.selectById(68L)).thenReturn(order);
        when(customerService.getByIdOrThrow(268L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderServiceDetailDTO dto = new OrderServiceDetailDTO();
        dto.setOrderId(68L);
        dto.setServiceRequirement("new content");
        dto.setServiceDetailJson("{\"serviceRequirement\":\"new content\"}");

        Order updated = orderService.updateServiceDetail(dto);

        JsonNode root = mapper.readTree(updated.getServiceDetailJson());
        assertThat(root.path("printAudit").path("status").asText()).isEqualTo("STALE");
        assertThat(root.has("confirmation")).isFalse();
        assertThat(root.has("serviceFormStatus")).isFalse();
    }

    @Test
    void updateServiceDetailShouldPersistServiceTemplateSnapshot() throws Exception {
        Order order = new Order();
        order.setId(16L);
        order.setCustomerId(216L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setVerificationStatus("VERIFIED");
        when(orderMapper.selectById(16L)).thenReturn(order);
        when(customerService.getByIdOrThrow(216L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderServiceDetailDTO dto = new OrderServiceDetailDTO();
        dto.setOrderId(16L);
        dto.setServiceRequirement("template snapshot must be fixed");
        dto.setServiceDetailJson("{\"serviceRequirement\":\"template snapshot must be fixed\"}");
        dto.setServiceTemplateId(8L);
        dto.setServiceTemplateBindingId(18L);
        dto.setServiceTemplateCode("PHOTO_CLASSIC");
        dto.setServiceTemplateName("肖像服务经典版");
        dto.setServiceTemplateTitle("到店服务确认单");
        dto.setServiceTemplateLayoutMode("classic");
        dto.setServiceTemplateConfigJson("{\"sections\":[\"基础信息\",\"客户签名\"]}");

        Order updated = orderService.updateServiceDetail(dto);

        JsonNode root = new ObjectMapper().readTree(updated.getServiceDetailJson());
        assertThat(root.path("serviceTemplate").path("templateId").asLong()).isEqualTo(8L);
        assertThat(root.path("serviceTemplate").path("bindingId").asLong()).isEqualTo(18L);
        assertThat(root.path("serviceTemplate").path("title").asText()).isEqualTo("到店服务确认单");
        assertThat(root.path("serviceTemplate").path("config").path("sections").get(1).asText()).isEqualTo("客户签名");
        assertThat(root.path("serviceTemplate").path("snapshotAt").asText()).isNotBlank();
    }

    @Test
    void verifyVoucherShouldRejectCompletedOrder() {
        Order order = new Order();
        order.setId(8L);
        order.setCustomerId(208L);
        order.setStatus(OrderStatus.COMPLETED.name());
        when(dbLockService.lockOrder(8L)).thenReturn(order);
        when(customerService.getByIdOrThrow(208L)).thenReturn(new Customer());

        OrderVoucherVerifyDTO dto = new OrderVoucherVerifyDTO();
        dto.setOrderId(8L);
        dto.setVerificationCode("DONE-8801");
        dto.setVerificationMethod("CODE");

        assertThatThrownBy(() -> orderService.verifyVoucher(dto, 9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only paid orders can be verified");
    }

    @Test
    void verifyVoucherShouldRequireCodeForCouponOrder() {
        Order order = new Order();
        order.setId(81L);
        order.setCustomerId(281L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setType(2);
        when(dbLockService.lockOrder(81L)).thenReturn(order);
        when(customerService.getByIdOrThrow(281L)).thenReturn(new Customer());

        OrderVoucherVerifyDTO dto = new OrderVoucherVerifyDTO();
        dto.setOrderId(81L);
        dto.setVerificationMethod("CODE");

        assertThatThrownBy(() -> orderService.verifyVoucher(dto, 9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("verification code is required");
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void verifyVoucherShouldBlockLocalUpdateWhenExternalGatewayFails() {
        Order order = new Order();
        order.setId(84L);
        order.setCustomerId(284L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setType(2);
        order.setSourceChannel(SourceChannel.DOUYIN.name());
        when(dbLockService.lockOrder(84L)).thenReturn(order);
        when(customerService.getByIdOrThrow(284L)).thenReturn(new Customer());
        when(voucherVerificationGateway.verify(any(Order.class), any(), any()))
                .thenThrow(new BusinessException("抖音核销失败，订单仍为待核销"));

        OrderVoucherVerifyDTO dto = new OrderVoucherVerifyDTO();
        dto.setOrderId(84L);
        dto.setVerificationCode("DY-8801");
        dto.setVerificationMethod("CODE");

        assertThatThrownBy(() -> orderService.verifyVoucher(dto, 9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("抖音核销失败")
                .hasMessageContaining("追踪编号");
        verify(orderMapper, never()).updateById(any(Order.class));
        ArgumentCaptor<OrderActionRecord> recordCaptor = ArgumentCaptor.forClass(OrderActionRecord.class);
        verify(orderActionRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getActionType()).isEqualTo("VOUCHER_VERIFY_FAILED");
        assertThat(recordCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.APPOINTMENT.name());
        assertThat(recordCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.APPOINTMENT.name());
        assertThat(recordCaptor.getValue().getRemark()).contains("抖音核销失败");
        assertThat(recordCaptor.getValue().getExtraJson())
                .contains("\"providerCode\":\"DOUYIN_LAIKE\"")
                .contains("\"externalVerified\":false")
                .contains("\"traceId\":\"VFY-");
    }

    @Test
    void verifyVoucherShouldRecordExternalProviderResultWhenGatewaySucceeds() throws Exception {
        Order order = new Order();
        order.setId(85L);
        order.setCustomerId(285L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setType(2);
        order.setSourceChannel(SourceChannel.DOUYIN.name());
        when(dbLockService.lockOrder(85L)).thenReturn(order);
        when(customerService.getByIdOrThrow(285L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        when(voucherVerificationGateway.verify(any(Order.class), any(), any()))
                .thenReturn(new OrderVoucherVerificationResult(
                        "DOUYIN_LAIKE", "MOCK", "VOUCHER_VERIFY:DOUYIN_LAIKE:85:DY-8802",
                        "{\"success\":true}", true));

        OrderVoucherVerifyDTO dto = new OrderVoucherVerifyDTO();
        dto.setOrderId(85L);
        dto.setVerificationCode("DY-8802");
        dto.setVerificationMethod("CODE");

        Order updated = orderService.verifyVoucher(dto, 9001L, "STORE_SERVICE");

        assertThat(updated.getVerificationStatus()).isEqualTo("VERIFIED");
        assertThat(updated.getVerificationCode()).isEqualTo("DY-8802");
        ArgumentCaptor<OrderActionRecord> recordCaptor = ArgumentCaptor.forClass(OrderActionRecord.class);
        verify(orderActionRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getActionType()).isEqualTo("EXTERNAL_VOUCHER_VERIFY");
        JsonNode extra = new ObjectMapper().readTree(recordCaptor.getValue().getExtraJson());
        assertThat(extra.path("providerCode").asText()).isEqualTo("DOUYIN_LAIKE");
        assertThat(extra.path("executionMode").asText()).isEqualTo("MOCK");
        assertThat(extra.path("externalVerified").asBoolean()).isTrue();
    }

    @Test
    void verifyVoucherShouldAllowDirectDepositWithoutSubmittedCode() {
        Order order = new Order();
        order.setId(82L);
        order.setCustomerId(282L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setType(1);
        when(dbLockService.lockOrder(82L)).thenReturn(order);
        when(customerService.getByIdOrThrow(282L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderVoucherVerifyDTO dto = new OrderVoucherVerifyDTO();
        dto.setOrderId(82L);
        dto.setVerificationMethod("DIRECT_DEPOSIT");

        Order updated = orderService.verifyVoucher(dto, 9001L);

        assertThat(updated.getVerificationStatus()).isEqualTo("VERIFIED");
        assertThat(updated.getVerificationCode()).isEqualTo("DIRECT-DEPOSIT-82");
        verify(voucherVerificationGateway, never()).verify(any(Order.class), any(), any());
    }

    @Test
    void verifyVoucherShouldRejectDirectDepositWhenConfigDisabled() {
        when(systemConfigService.getBoolean("deposit.direct.enabled", true)).thenReturn(false);
        Order order = new Order();
        order.setId(83L);
        order.setCustomerId(283L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setType(1);
        when(dbLockService.lockOrder(83L)).thenReturn(order);
        when(customerService.getByIdOrThrow(283L)).thenReturn(new Customer());

        OrderVoucherVerifyDTO dto = new OrderVoucherVerifyDTO();
        dto.setOrderId(83L);
        dto.setVerificationMethod("DIRECT_DEPOSIT");

        assertThatThrownBy(() -> orderService.verifyVoucher(dto, 9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("direct deposit verification is disabled");
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    private String printedServiceDetail(ObjectMapper mapper, String serviceRequirement, boolean confirmed) {
        ObjectNode root = mapper.createObjectNode();
        root.put("serviceRequirement", serviceRequirement);
        String hash = ServiceFormVersionSupport.printableHash(root, mapper);
        ObjectNode printAudit = mapper.createObjectNode();
        printAudit.put("status", ServiceFormVersionSupport.PRINT_STATUS_PRINTED);
        printAudit.put("serviceDetailHash", hash);
        printAudit.put("projectionVersion", ServiceFormVersionSupport.PROJECTION_VERSION);
        printAudit.put("printCount", 1);
        root.set("printAudit", printAudit);
        if (confirmed) {
            ObjectNode confirmation = mapper.createObjectNode();
            confirmation.put("status", ServiceFormVersionSupport.CONFIRM_STATUS);
            confirmation.put("serviceDetailHash", hash);
            confirmation.put("projectionVersion", ServiceFormVersionSupport.PROJECTION_VERSION);
            root.set("confirmation", confirmation);
            root.put("serviceFormStatus", ServiceFormVersionSupport.CONFIRM_STATUS);
        }
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

