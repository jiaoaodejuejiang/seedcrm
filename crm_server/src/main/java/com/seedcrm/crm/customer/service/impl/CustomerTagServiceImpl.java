package com.seedcrm.crm.customer.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.entity.CustomerTagRule;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.customer.mapper.CustomerTagRuleMapper;
import com.seedcrm.crm.customer.service.CustomerTagService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class CustomerTagServiceImpl implements CustomerTagService {

    private static final Pattern RULE_PATTERN = Pattern.compile("^\\s*(>=|<=|=|>|<)\\s*(.+?)\\s*$");
    private static final Map<String, Function<CustomerOrderStats, Comparable<?>>> METRIC_EXTRACTORS = Map.of(
            "ORDER_COUNT", stats -> stats.orderCount(),
            "TOTAL_AMOUNT", stats -> stats.totalAmount(),
            "LAST_ORDER_DAYS", stats -> stats.lastOrderDays());

    private final CustomerMapper customerMapper;
    private final CustomerTagRuleMapper customerTagRuleMapper;
    private final OrderMapper orderMapper;

    public CustomerTagServiceImpl(CustomerMapper customerMapper,
                                  CustomerTagRuleMapper customerTagRuleMapper,
                                  OrderMapper orderMapper) {
        this.customerMapper = customerMapper;
        this.customerTagRuleMapper = customerTagRuleMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public Customer updateTag(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new BusinessException("customerId is required");
        }

        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("customer not found");
        }

        CustomerOrderStats stats = buildStats(customerId);
        List<CustomerTagRule> rules = customerTagRuleMapper.selectList(Wrappers.<CustomerTagRule>lambdaQuery()
                .orderByAsc(CustomerTagRule::getPriority)
                .orderByAsc(CustomerTagRule::getId));

        String matchedTag = null;
        for (CustomerTagRule rule : rules) {
            if (matches(rule, stats)) {
                matchedTag = rule.getTagCode();
                break;
            }
        }

        customer.setTag(StringUtils.hasText(matchedTag) ? matchedTag : null);
        customer.setUpdateTime(LocalDateTime.now());
        if (customerMapper.updateById(customer) <= 0) {
            throw new BusinessException("failed to update customer tag");
        }

        log.info("customer tag updated, customerId={}, tag={}", customerId, customer.getTag());
        return customer;
    }

    private CustomerOrderStats buildStats(Long customerId) {
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getCustomerId, customerId)
                .orderByAsc(Order::getId));

        List<Order> activeOrders = orders.stream()
                .filter(this::isActiveOrder)
                .toList();

        long orderCount = activeOrders.size();
        BigDecimal totalAmount = activeOrders.stream()
                .map(Order::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime lastOrderTime = activeOrders.stream()
                .map(this::resolveOrderTime)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Long lastOrderDays = lastOrderTime == null
                ? null
                : ChronoUnit.DAYS.between(lastOrderTime, LocalDateTime.now());

        return new CustomerOrderStats(orderCount, totalAmount, lastOrderTime, lastOrderDays);
    }

    private boolean matches(CustomerTagRule rule, CustomerOrderStats stats) {
        if (rule == null || !StringUtils.hasText(rule.getTagCode()) || !StringUtils.hasText(rule.getRuleType())
                || !StringUtils.hasText(rule.getRuleValue())) {
            return false;
        }

        Function<CustomerOrderStats, Comparable<?>> extractor = METRIC_EXTRACTORS.get(rule.getRuleType());
        if (extractor == null) {
            log.warn("skip unsupported tag rule type: {}", rule.getRuleType());
            return false;
        }

        Comparable<?> actualValue = extractor.apply(stats);
        if (actualValue == null) {
            return false;
        }

        RuleExpression expression = parseExpression(rule.getRuleValue(), actualValue);
        return compare(actualValue, expression);
    }

    private RuleExpression parseExpression(String ruleValue, Comparable<?> actualValue) {
        Matcher matcher = RULE_PATTERN.matcher(ruleValue);
        if (!matcher.matches()) {
            throw new BusinessException("invalid customer tag rule expression: " + ruleValue);
        }

        String operator = matcher.group(1);
        String rawValue = matcher.group(2);
        Comparable<?> expectedValue = parseExpectedValue(rawValue, actualValue);
        return new RuleExpression(operator, expectedValue);
    }

    private Comparable<?> parseExpectedValue(String rawValue, Comparable<?> actualValue) {
        if (actualValue instanceof BigDecimal) {
            return new BigDecimal(rawValue);
        }
        if (actualValue instanceof Long) {
            return Long.valueOf(rawValue);
        }
        if (actualValue instanceof Integer) {
            return Integer.valueOf(rawValue);
        }
        throw new BusinessException("unsupported customer tag rule metric type");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean compare(Comparable actualValue, RuleExpression expression) {
        int result = actualValue.compareTo(expression.expectedValue());
        return switch (expression.operator()) {
            case ">" -> result > 0;
            case ">=" -> result >= 0;
            case "<" -> result < 0;
            case "<=" -> result <= 0;
            case "=" -> result == 0;
            default -> throw new BusinessException("unsupported customer tag rule operator: " + expression.operator());
        };
    }

    private boolean isActiveOrder(Order order) {
        if (order == null || !StringUtils.hasText(order.getStatus())) {
            return false;
        }
        return !OrderStatus.CANCELLED.name().equals(order.getStatus())
                && !OrderStatus.REFUNDED.name().equals(order.getStatus());
    }

    private LocalDateTime resolveOrderTime(Order order) {
        if (order == null) {
            return null;
        }
        return order.getCompleteTime() != null ? order.getCompleteTime() : order.getCreateTime();
    }

    private record CustomerOrderStats(long orderCount,
                                      BigDecimal totalAmount,
                                      LocalDateTime lastOrderTime,
                                      Long lastOrderDays) {
    }

    private record RuleExpression(String operator, Comparable<?> expectedValue) {
    }
}
