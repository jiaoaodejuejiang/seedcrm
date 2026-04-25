package com.seedcrm.crm.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.service.OrderSettlementService;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.risk.service.DbLockService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderMapper, clueMapper, customerService, customerTagService,
                planOrderMapper, distributorIncomeService, dbLockService, orderSettlementService);
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
    void createOrderShouldNormalizeCouponTypeAndDefaultFullDeposit() {
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

        assertThat(order.getType()).isEqualTo(2);
        assertThat(order.getDeposit()).isEqualByComparingTo("1280.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID_DEPOSIT.name());
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
    void appointmentShouldAllowRescheduleWhenAlreadyAppointment() {
        Order order = new Order();
        order.setId(5L);
        order.setOrderNo("ORD202604211234567895");
        order.setCustomerId(205L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setAppointmentTime(LocalDateTime.of(2026, 4, 25, 10, 0));
        when(orderMapper.selectById(5L)).thenReturn(order);
        when(customerService.getByIdOrThrow(205L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderAppointmentDTO dto = new OrderAppointmentDTO();
        dto.setOrderId(5L);
        dto.setAppointmentTime(LocalDateTime.of(2026, 4, 26, 11, 30));
        dto.setRemark("reschedule");

        Order updated = orderService.appointment(dto);

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.APPOINTMENT.name());
        assertThat(updated.getAppointmentTime()).isEqualTo(LocalDateTime.of(2026, 4, 26, 11, 30));
        verify(customerService).refreshCustomerLifecycle(205L);
    }

    @Test
    void cancelAppointmentShouldRevertOrderToPaidDeposit() {
        Order order = new Order();
        order.setId(7L);
        order.setOrderNo("ORD202604211234567897");
        order.setCustomerId(207L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        order.setAppointmentTime(LocalDateTime.of(2026, 4, 25, 15, 0));
        when(orderMapper.selectById(7L)).thenReturn(order);
        when(customerService.getByIdOrThrow(207L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderActionDTO dto = new OrderActionDTO();
        dto.setOrderId(7L);
        dto.setRemark("cancel appointment");

        Order updated = orderService.cancelAppointment(dto);

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID_DEPOSIT.name());
        assertThat(updated.getAppointmentTime()).isNull();
        verify(customerService).refreshCustomerLifecycle(207L);
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
    void updateServiceDetailShouldPersistServiceRequirement() {
        Order order = new Order();
        order.setId(6L);
        order.setCustomerId(206L);
        order.setStatus(OrderStatus.APPOINTMENT.name());
        when(orderMapper.selectById(6L)).thenReturn(order);
        when(customerService.getByIdOrThrow(206L)).thenReturn(new Customer());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderServiceDetailDTO dto = new OrderServiceDetailDTO();
        dto.setOrderId(6L);
        dto.setServiceRequirement("客户到店后先做面诊，再确认套餐细项");

        Order updated = orderService.updateServiceDetail(dto);

        assertThat(updated.getRemark()).isEqualTo("客户到店后先做面诊，再确认套餐细项");
        verify(customerService).refreshCustomerLifecycle(206L);
    }
}
