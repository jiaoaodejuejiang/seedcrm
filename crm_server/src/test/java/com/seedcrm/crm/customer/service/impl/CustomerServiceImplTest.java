package com.seedcrm.crm.customer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.enums.CustomerStatus;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private OrderMapper orderMapper;

    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(customerMapper, orderMapper);
    }

    @Test
    void getOrCreateByClueShouldCreateNewCustomer() {
        Clue clue = new Clue();
        clue.setId(10L);
        clue.setName("Alice");
        clue.setPhone("13800138000");
        clue.setWechat("alice-wechat");

        when(customerMapper.selectOne(any())).thenReturn(null);
        when(customerMapper.insert(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(1L);
            return 1;
        });

        Customer customer = customerService.getOrCreateByClue(clue);

        assertThat(customer.getId()).isEqualTo(1L);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.NEW.name());
        assertThat(customer.getPhone()).isEqualTo("13800138000");
    }

    @Test
    void refreshCustomerLifecycleShouldPromoteToPaidDealAndRepeat() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setPhone("13800138000");
        when(customerMapper.selectById(1L)).thenReturn(customer);

        Order paidOrder = new Order();
        paidOrder.setId(11L);
        paidOrder.setCustomerId(1L);
        paidOrder.setStatus(OrderStatus.PAID_DEPOSIT.name());
        paidOrder.setCreateTime(LocalDateTime.of(2026, 4, 21, 10, 0));

        when(orderMapper.selectList(any())).thenReturn(List.of(paidOrder));
        when(customerMapper.updateById(any(Customer.class))).thenReturn(1);

        Customer paidCustomer = customerService.refreshCustomerLifecycle(1L);

        assertThat(paidCustomer.getStatus()).isEqualTo(CustomerStatus.PAID.name());
        assertThat(paidCustomer.getFirstOrderTime()).isEqualTo(paidOrder.getCreateTime());
        assertThat(paidCustomer.getLastOrderTime()).isEqualTo(paidOrder.getCreateTime());

        Order completedOrder = new Order();
        completedOrder.setId(12L);
        completedOrder.setCustomerId(1L);
        completedOrder.setStatus(OrderStatus.COMPLETED.name());
        completedOrder.setCreateTime(LocalDateTime.of(2026, 4, 21, 11, 0));

        when(orderMapper.selectList(any())).thenReturn(List.of(completedOrder));
        Customer dealCustomer = customerService.refreshCustomerLifecycle(1L);
        assertThat(dealCustomer.getStatus()).isEqualTo(CustomerStatus.DEAL.name());

        Order secondOrder = new Order();
        secondOrder.setId(13L);
        secondOrder.setCustomerId(1L);
        secondOrder.setStatus(OrderStatus.CREATED.name());
        secondOrder.setCreateTime(LocalDateTime.of(2026, 4, 21, 12, 0));

        when(orderMapper.selectList(any())).thenReturn(List.of(completedOrder, secondOrder));
        Customer repeatCustomer = customerService.refreshCustomerLifecycle(1L);
        assertThat(repeatCustomer.getStatus()).isEqualTo(CustomerStatus.REPEAT.name());
        assertThat(repeatCustomer.getFirstOrderTime()).isEqualTo(completedOrder.getCreateTime());
        assertThat(repeatCustomer.getLastOrderTime()).isEqualTo(secondOrder.getCreateTime());

        verify(customerMapper, times(3)).updateById(any(Customer.class));
    }
}
