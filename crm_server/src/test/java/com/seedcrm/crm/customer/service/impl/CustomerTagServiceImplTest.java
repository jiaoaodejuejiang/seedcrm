package com.seedcrm.crm.customer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.entity.CustomerTagRule;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.customer.mapper.CustomerTagRuleMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerTagServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private CustomerTagRuleMapper customerTagRuleMapper;

    @Mock
    private OrderMapper orderMapper;

    private CustomerTagServiceImpl customerTagService;

    @BeforeEach
    void setUp() {
        customerTagService = new CustomerTagServiceImpl(customerMapper, customerTagRuleMapper, orderMapper);
    }

    @Test
    void updateTagShouldPickFirstMatchedRuleByPriority() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setTag("OLD");
        when(customerMapper.selectById(1L)).thenReturn(customer);

        Order firstOrder = completedOrder(1L, new BigDecimal("600.00"), LocalDateTime.now().minusDays(2));
        Order secondOrder = completedOrder(2L, new BigDecimal("500.00"), LocalDateTime.now().minusDays(1));
        when(orderMapper.selectList(any())).thenReturn(List.of(firstOrder, secondOrder));

        CustomerTagRule highValue = rule(10L, "HIGH_VALUE", "TOTAL_AMOUNT", ">=1000", 1);
        CustomerTagRule repeat = rule(11L, "REPEAT", "ORDER_COUNT", ">=2", 2);
        when(customerTagRuleMapper.selectList(any())).thenReturn(List.of(highValue, repeat));
        when(customerMapper.updateById(any(Customer.class))).thenReturn(1);

        Customer updated = customerTagService.updateTag(1L);

        assertThat(updated.getTag()).isEqualTo("HIGH_VALUE");
        verify(customerMapper).updateById(customer);
    }

    @Test
    void updateTagShouldSupportLastOrderDaysAndClearWhenNoMatch() {
        Customer customer = new Customer();
        customer.setId(2L);
        customer.setTag("HIGH_VALUE");
        when(customerMapper.selectById(2L)).thenReturn(customer);

        Order oldOrder = completedOrder(3L, new BigDecimal("300.00"), LocalDateTime.now().minusDays(45));
        when(orderMapper.selectList(any())).thenReturn(List.of(oldOrder));

        CustomerTagRule sleep = rule(20L, "SLEEP", "LAST_ORDER_DAYS", ">30", 1);
        when(customerTagRuleMapper.selectList(any())).thenReturn(List.of(sleep));
        when(customerMapper.updateById(any(Customer.class))).thenReturn(1);

        Customer updated = customerTagService.updateTag(2L);
        assertThat(updated.getTag()).isEqualTo("SLEEP");

        when(customerTagRuleMapper.selectList(any())).thenReturn(List.of(rule(21L, "REPEAT", "ORDER_COUNT", ">=2", 1)));
        Customer cleared = customerTagService.updateTag(2L);
        assertThat(cleared.getTag()).isNull();
    }

    @Test
    void updateTagShouldRejectInvalidRuleExpression() {
        Customer customer = new Customer();
        customer.setId(3L);
        when(customerMapper.selectById(3L)).thenReturn(customer);
        when(orderMapper.selectList(any())).thenReturn(List.of(completedOrder(4L, new BigDecimal("100.00"), LocalDateTime.now())));
        when(customerTagRuleMapper.selectList(any())).thenReturn(List.of(rule(30L, "BROKEN", "ORDER_COUNT", "two", 1)));

        assertThatThrownBy(() -> customerTagService.updateTag(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid customer tag rule expression");
    }

    private Order completedOrder(Long id, BigDecimal amount, LocalDateTime completeTime) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerId(1L);
        order.setAmount(amount);
        order.setStatus(OrderStatus.COMPLETED.name());
        order.setCreateTime(completeTime.minusHours(1));
        order.setCompleteTime(completeTime);
        return order;
    }

    private CustomerTagRule rule(Long id, String tagCode, String ruleType, String ruleValue, Integer priority) {
        CustomerTagRule rule = new CustomerTagRule();
        rule.setId(id);
        rule.setTagCode(tagCode);
        rule.setRuleType(ruleType);
        rule.setRuleValue(ruleValue);
        rule.setPriority(priority);
        return rule;
    }
}
