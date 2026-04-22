package com.seedcrm.crm.customer.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.clue.enums.SourceChannel;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.dto.CustomerExportDTO;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.entity.CustomerTagDetail;
import com.seedcrm.crm.customer.enums.CustomerStatus;
import com.seedcrm.crm.customer.mapper.CustomerTagDetailMapper;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.customer.service.CustomerService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    private final CustomerMapper customerMapper;
    private final OrderMapper orderMapper;
    private final CustomerTagDetailMapper customerTagDetailMapper;

    public CustomerServiceImpl(CustomerMapper customerMapper,
                               OrderMapper orderMapper,
                               CustomerTagDetailMapper customerTagDetailMapper) {
        this.customerMapper = customerMapper;
        this.orderMapper = orderMapper;
        this.customerTagDetailMapper = customerTagDetailMapper;
    }

    @Override
    public Customer getByIdOrThrow(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new BusinessException("customerId is required");
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("customer not found");
        }
        return customer;
    }

    @Override
    public Customer getByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return customerMapper.selectOne(Wrappers.<Customer>lambdaQuery()
                .eq(Customer::getPhone, phone)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional
    public Customer getOrCreateByClue(Clue clue) {
        if (clue == null || clue.getId() == null) {
            throw new BusinessException("clue is required");
        }
        if (!StringUtils.hasText(clue.getPhone())) {
            throw new BusinessException("clue phone is required for customer conversion");
        }

        Customer customer = getByPhone(clue.getPhone());
        LocalDateTime now = LocalDateTime.now();
        if (customer == null) {
            customer = new Customer();
            customer.setName(clue.getName());
            customer.setPhone(clue.getPhone());
            customer.setWechat(clue.getWechat());
            customer.setSourceClueId(clue.getId());
            customer.setSourceChannel(SourceChannel.resolveCode(clue.getSourceChannel(), clue.getSource()));
            customer.setSourceId(clue.getSourceId());
            customer.setStatus(CustomerStatus.NEW.name());
            customer.setCreateTime(now);
            customer.setUpdateTime(now);
            if (customerMapper.insert(customer) <= 0) {
                throw new BusinessException("failed to create customer");
            }
            return customer;
        }

        boolean changed = false;
        if (!StringUtils.hasText(customer.getName()) && StringUtils.hasText(clue.getName())) {
            customer.setName(clue.getName());
            changed = true;
        }
        if (!StringUtils.hasText(customer.getWechat()) && StringUtils.hasText(clue.getWechat())) {
            customer.setWechat(clue.getWechat());
            changed = true;
        }
        if (customer.getSourceClueId() == null) {
            customer.setSourceClueId(clue.getId());
            changed = true;
        }
        if (!StringUtils.hasText(customer.getSourceChannel())) {
            customer.setSourceChannel(SourceChannel.resolveCode(clue.getSourceChannel(), clue.getSource()));
            changed = true;
        }
        if (customer.getSourceId() == null && clue.getSourceId() != null) {
            customer.setSourceId(clue.getSourceId());
            changed = true;
        }
        if (!StringUtils.hasText(customer.getStatus())) {
            customer.setStatus(CustomerStatus.NEW.name());
            changed = true;
        }
        if (changed) {
            customer.setUpdateTime(now);
            if (customerMapper.updateById(customer) <= 0) {
                throw new BusinessException("failed to sync customer");
            }
        }
        return customer;
    }

    @Override
    public List<CustomerExportDTO> exportCustomers() {
        List<Customer> customers = customerMapper.selectList(Wrappers.<Customer>lambdaQuery()
                .orderByDesc(Customer::getId));
        if (customers.isEmpty()) {
            return List.of();
        }

        List<Long> customerIds = customers.stream().map(Customer::getId).filter(Objects::nonNull).toList();
        Map<Long, List<String>> tagMap = customerTagDetailMapper.selectList(Wrappers.<CustomerTagDetail>lambdaQuery()
                        .in(CustomerTagDetail::getCustomerId, customerIds)
                        .orderByAsc(CustomerTagDetail::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        CustomerTagDetail::getCustomerId,
                        Collectors.mapping(CustomerTagDetail::getTagCode, Collectors.toList())));

        return customers.stream().map(customer -> {
            CustomerExportDTO exportDTO = new CustomerExportDTO();
            exportDTO.setId(customer.getId());
            exportDTO.setName(customer.getName());
            exportDTO.setPhone(customer.getPhone());
            exportDTO.setWechat(customer.getWechat());
            exportDTO.setSourceClueId(customer.getSourceClueId());
            exportDTO.setSourceChannel(customer.getSourceChannel());
            exportDTO.setSourceId(customer.getSourceId());
            exportDTO.setStatus(customer.getStatus());
            exportDTO.setLevel(customer.getLevel());

            List<String> tags = new ArrayList<>(tagMap.getOrDefault(customer.getId(), List.of()));
            if (tags.isEmpty() && StringUtils.hasText(customer.getTag())) {
                tags.add(customer.getTag());
            }
            exportDTO.setTags(tags);
            return exportDTO;
        }).toList();
    }

    @Override
    @Transactional
    public Customer refreshCustomerLifecycle(Long customerId) {
        Customer customer = getByIdOrThrow(customerId);
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getCustomerId, customerId)
                .orderByAsc(Order::getCreateTime)
                .orderByAsc(Order::getId));

        LocalDateTime firstOrderTime = orders.stream()
                .map(Order::getCreateTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDateTime lastOrderTime = orders.stream()
                .map(Order::getCreateTime)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        long activeOrderCount = orders.stream().filter(this::isActiveOrder).count();
        boolean hasCompletedOrder = orders.stream()
                .filter(this::isActiveOrder)
                .anyMatch(order -> OrderStatus.COMPLETED.name().equals(order.getStatus()));
        boolean hasPaidOrder = orders.stream()
                .filter(this::isActiveOrder)
                .anyMatch(this::isPaidStageOrder);

        CustomerStatus targetStatus = CustomerStatus.NEW;
        if (activeOrderCount > 1) {
            targetStatus = CustomerStatus.REPEAT;
        } else if (hasCompletedOrder) {
            targetStatus = CustomerStatus.DEAL;
        } else if (hasPaidOrder) {
            targetStatus = CustomerStatus.PAID;
        }

        customer.setStatus(targetStatus.name());
        customer.setFirstOrderTime(firstOrderTime);
        customer.setLastOrderTime(lastOrderTime);
        customer.setUpdateTime(LocalDateTime.now());
        if (customerMapper.updateById(customer) <= 0) {
            throw new BusinessException("failed to refresh customer lifecycle");
        }
        return customer;
    }

    private boolean isActiveOrder(Order order) {
        if (order == null || !StringUtils.hasText(order.getStatus())) {
            return false;
        }
        return !OrderStatus.CANCELLED.name().equals(order.getStatus())
                && !OrderStatus.REFUNDED.name().equals(order.getStatus());
    }

    private boolean isPaidStageOrder(Order order) {
        if (order == null || !StringUtils.hasText(order.getStatus())) {
            return false;
        }
        return OrderStatus.PAID_DEPOSIT.name().equals(order.getStatus())
                || OrderStatus.APPOINTMENT.name().equals(order.getStatus())
                || OrderStatus.ARRIVED.name().equals(order.getStatus())
                || OrderStatus.SERVING.name().equals(order.getStatus())
                || OrderStatus.COMPLETED.name().equals(order.getStatus());
    }
}
