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
import com.seedcrm.crm.order.dto.OrderActionDTO;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.dto.OrderPayDTO;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
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

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderMapper, clueMapper, customerService, customerTagService,
                planOrderMapper);
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
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED.name());
        assertThat(order.getSourceChannel()).isEqualTo(SourceChannel.DISTRIBUTOR.name());
        assertThat(order.getSourceId()).isEqualTo(900L);
        verify(customerService).getOrCreateByClue(clue);
        verify(customerService).refreshCustomerLifecycle(200L);
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
    void completeShouldRefreshCustomerLifecycle() {
        Order order = new Order();
        order.setId(2L);
        order.setOrderNo("ORD202604211234567891");
        order.setCustomerId(202L);
        order.setStatus(OrderStatus.SERVING.name());
        when(orderMapper.selectById(2L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        when(customerService.getByIdOrThrow(202L)).thenReturn(new Customer());
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(9L);
        planOrder.setOrderId(2L);
        planOrder.setStatus(PlanOrderStatus.FINISHED.name());
        when(planOrderMapper.selectOne(any())).thenReturn(planOrder);

        OrderActionDTO dto = new OrderActionDTO();
        dto.setOrderId(2L);
        dto.setRemark("done");

        Order completedOrder = orderService.complete(dto);

        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED.name());
        assertThat(completedOrder.getCompleteTime()).isNotNull();
        verify(customerService, times(1)).getByIdOrThrow(202L);
        verify(customerService).refreshCustomerLifecycle(202L);
        verify(customerTagService).updateTag(202L);
    }

    @Test
    void completeShouldRejectWhenPlanOrderNotFinished() {
        Order order = new Order();
        order.setId(3L);
        order.setOrderNo("ORD202604211234567892");
        order.setCustomerId(203L);
        order.setStatus(OrderStatus.SERVING.name());
        when(orderMapper.selectById(3L)).thenReturn(order);
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
}
