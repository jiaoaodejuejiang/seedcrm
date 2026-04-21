package com.seedcrm.crm.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.dto.OrderActionDTO;
import com.seedcrm.crm.order.dto.OrderAppointmentDTO;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.dto.OrderPayDTO;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
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

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderMapper, clueMapper);
    }

    @Test
    void createOrderShouldInsertCreatedOrder() {
        Clue clue = new Clue();
        clue.setId(100L);
        when(clueMapper.selectById(100L)).thenReturn(clue);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return 1;
        });

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setClueId(100L);
        dto.setType(1);
        dto.setAmount(new BigDecimal("888.00"));
        dto.setDeposit(new BigDecimal("88.00"));
        dto.setRemark("new order");

        Order order = orderService.createOrder(dto);

        assertThat(order.getId()).isEqualTo(1L);
        assertThat(order.getOrderNo()).startsWith("ORD");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED.name());
        assertThat(order.getCreateTime()).isNotNull();
        assertThat(order.getUpdateTime()).isNotNull();
        verify(orderMapper).insert(any(Order.class));
    }

    @Test
    void payDepositShouldRejectInvalidTransition() {
        Order order = new Order();
        order.setId(1L);
        order.setAmount(new BigDecimal("500.00"));
        order.setDeposit(new BigDecimal("50.00"));
        order.setStatus(OrderStatus.PAID_DEPOSIT.name());
        when(orderMapper.selectById(1L)).thenReturn(order);

        OrderPayDTO dto = new OrderPayDTO();
        dto.setOrderId(1L);
        dto.setDeposit(new BigDecimal("50.00"));

        assertThatThrownBy(() -> orderService.payDeposit(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单状态错误");
    }

    @Test
    void orderLifecycleShouldCompleteAfterServing() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("ORD202604211234567890");
        order.setAmount(new BigDecimal("1000.00"));
        order.setDeposit(BigDecimal.ZERO);
        order.setStatus(OrderStatus.CREATED.name());
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderPayDTO payDTO = new OrderPayDTO();
        payDTO.setOrderId(1L);
        payDTO.setDeposit(new BigDecimal("100.00"));
        orderService.payDeposit(payDTO);

        OrderAppointmentDTO appointmentDTO = new OrderAppointmentDTO();
        appointmentDTO.setOrderId(1L);
        appointmentDTO.setAppointmentTime(LocalDateTime.now().plusDays(1));
        orderService.appointment(appointmentDTO);

        OrderActionDTO arriveDTO = new OrderActionDTO();
        arriveDTO.setOrderId(1L);
        orderService.arrive(arriveDTO);

        OrderActionDTO servingDTO = new OrderActionDTO();
        servingDTO.setOrderId(1L);
        orderService.serving(servingDTO);

        OrderActionDTO completeDTO = new OrderActionDTO();
        completeDTO.setOrderId(1L);
        Order completedOrder = orderService.complete(completeDTO);

        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED.name());
        assertThat(completedOrder.getAppointmentTime()).isNotNull();
        assertThat(completedOrder.getArriveTime()).isNotNull();
        assertThat(completedOrder.getCompleteTime()).isNotNull();
        verify(orderMapper, times(5)).updateById(any(Order.class));
    }

    @Test
    void cancelShouldRejectCompletedOrder() {
        Order order = new Order();
        order.setId(2L);
        order.setStatus(OrderStatus.COMPLETED.name());
        when(orderMapper.selectById(2L)).thenReturn(order);

        OrderActionDTO dto = new OrderActionDTO();
        dto.setOrderId(2L);

        assertThatThrownBy(() -> orderService.cancel(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不允许取消订单");
    }
}
