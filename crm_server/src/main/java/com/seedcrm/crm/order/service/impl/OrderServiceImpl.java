package com.seedcrm.crm.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.service.OrderSettlementService;
import com.seedcrm.crm.order.service.OrderService;
import com.seedcrm.crm.order.util.OrderNoGenerator;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.risk.service.DbLockService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final OrderMapper orderMapper;
    private final ClueMapper clueMapper;
    private final CustomerService customerService;
    private final CustomerTagService customerTagService;
    private final PlanOrderMapper planOrderMapper;
    private final DistributorIncomeService distributorIncomeService;
    private final DbLockService dbLockService;
    private final OrderSettlementService orderSettlementService;

    public OrderServiceImpl(OrderMapper orderMapper,
                            ClueMapper clueMapper,
                            CustomerService customerService,
                            CustomerTagService customerTagService,
                            PlanOrderMapper planOrderMapper,
                            DistributorIncomeService distributorIncomeService,
                            DbLockService dbLockService,
                            OrderSettlementService orderSettlementService) {
        this.orderMapper = orderMapper;
        this.clueMapper = clueMapper;
        this.customerService = customerService;
        this.customerTagService = customerTagService;
        this.planOrderMapper = planOrderMapper;
        this.distributorIncomeService = distributorIncomeService;
        this.dbLockService = dbLockService;
        this.orderSettlementService = orderSettlementService;
    }

    @Override
    @Transactional
    public Order createOrder(OrderCreateDTO orderCreateDTO) {
        validateCreateRequest(orderCreateDTO);

        Clue clue = getClueIfPresent(orderCreateDTO.getClueId());
        Customer customer = resolveCustomer(orderCreateDTO, clue);
        if (customer == null || customer.getId() == null) {
            throw new BusinessException("customer must be bound before creating order");
        }
        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.generate());
        order.setClueId(orderCreateDTO.getClueId());
        order.setCustomerId(customer == null ? null : customer.getId());
        inheritSource(order, clue, customer);
        order.setType(OrderType.normalizeCode(orderCreateDTO.getType()));
        order.setAmount(orderCreateDTO.getAmount());
        order.setDeposit(defaultDeposit(orderCreateDTO));
        order.setStatus(resolveInitialStatus(order).name());
        order.setRemark(orderCreateDTO.getRemark());
        order.setCreateTime(now);
        order.setUpdateTime(now);
        if (orderMapper.insert(order) <= 0) {
            throw new BusinessException("failed to create order");
        }

        markClueConverted(clue);
        refreshCustomerLifecycle(order.getCustomerId());
        log.info("order created, orderNo={}, customerId={}, status={}",
                order.getOrderNo(), order.getCustomerId(), order.getStatus());
        return order;
    }

    @Override
    @Transactional
    public Order payDeposit(OrderPayDTO orderPayDTO) {
        validateOrderId(orderPayDTO == null ? null : orderPayDTO.getOrderId());
        Order order = getOrderById(orderPayDTO.getOrderId());
        ensureOrderCustomerBound(order);
        assertNextStatus(order, OrderStatus.PAID_DEPOSIT);

        BigDecimal deposit = orderPayDTO.getDeposit() == null ? order.getDeposit() : orderPayDTO.getDeposit();
        if (deposit == null || deposit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("deposit must be greater than 0");
        }
        if (deposit.compareTo(order.getAmount()) > 0) {
            throw new BusinessException("deposit cannot exceed order amount");
        }

        order.setDeposit(deposit);
        updateRemark(order, orderPayDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.PAID_DEPOSIT);
    }

    @Override
    @Transactional
    public Order appointment(OrderAppointmentDTO orderAppointmentDTO) {
        validateOrderId(orderAppointmentDTO == null ? null : orderAppointmentDTO.getOrderId());
        if (orderAppointmentDTO.getAppointmentTime() == null) {
            throw new BusinessException("appointmentTime is required");
        }

        Order order = getOrderById(orderAppointmentDTO.getOrderId());
        ensureOrderCustomerBound(order);
        assertNextStatus(order, OrderStatus.APPOINTMENT);
        order.setAppointmentTime(orderAppointmentDTO.getAppointmentTime());
        updateRemark(order, orderAppointmentDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.APPOINTMENT);
    }

    @Override
    @Transactional
    public Order arrive(OrderActionDTO orderActionDTO) {
        Order order = validateAndGetActionOrder(orderActionDTO, OrderStatus.ARRIVED);
        order.setArriveTime(LocalDateTime.now());
        updateRemark(order, orderActionDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.ARRIVED);
    }

    @Override
    @Transactional
    public Order serving(OrderActionDTO orderActionDTO) {
        Order order = validateAndGetActionOrder(orderActionDTO, OrderStatus.SERVING);
        updateRemark(order, orderActionDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.SERVING);
    }

    @Override
    @Transactional
    public Order complete(OrderActionDTO orderActionDTO) {
        validateOrderId(orderActionDTO == null ? null : orderActionDTO.getOrderId());
        Order order = dbLockService.lockOrder(orderActionDTO.getOrderId());
        ensureOrderCustomerBound(order);
        if (OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            return orderSettlementService.settleCompletedOrder(order.getId());
        }
        assertNextStatus(order, OrderStatus.COMPLETED);
        ensurePlanOrderFinished(order.getId());
        order.setCompleteTime(LocalDateTime.now());
        updateRemark(order, orderActionDTO.getRemark());
        updateOrderStatus(order, OrderStatus.COMPLETED);
        return orderSettlementService.settleCompletedOrder(order.getId());
    }

    @Override
    @Transactional
    public Order cancel(OrderActionDTO orderActionDTO) {
        validateOrderId(orderActionDTO == null ? null : orderActionDTO.getOrderId());
        Order order = getOrderById(orderActionDTO.getOrderId());
        ensureOrderCustomerBound(order);
        OrderStatus currentStatus = getCurrentStatus(order);
        if (!currentStatus.canCancel()) {
            throw new BusinessException("order cannot be cancelled from status " + currentStatus.name());
        }
        updateRemark(order, orderActionDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.CANCELLED);
    }

    @Override
    @Transactional
    public Order refund(OrderActionDTO orderActionDTO) {
        validateOrderId(orderActionDTO == null ? null : orderActionDTO.getOrderId());
        Order order = getOrderById(orderActionDTO.getOrderId());
        ensureOrderCustomerBound(order);
        OrderStatus currentStatus = getCurrentStatus(order);
        if (!currentStatus.canRefund()) {
            throw new BusinessException("order cannot be refunded from status " + currentStatus.name());
        }
        updateRemark(order, orderActionDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.REFUNDED);
    }

    private void validateCreateRequest(OrderCreateDTO orderCreateDTO) {
        if (orderCreateDTO == null) {
            throw new BusinessException("request body is required");
        }
        if (orderCreateDTO.getClueId() == null && orderCreateDTO.getCustomerId() == null) {
            throw new BusinessException("clueId or customerId is required");
        }
        if (!OrderType.isValid(orderCreateDTO.getType())) {
            throw new BusinessException("invalid order type");
        }
        if (orderCreateDTO.getAmount() == null || orderCreateDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("amount must be greater than 0");
        }
        if (orderCreateDTO.getDeposit() != null && orderCreateDTO.getDeposit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("deposit cannot be negative");
        }
        if (orderCreateDTO.getDeposit() != null
                && orderCreateDTO.getDeposit().compareTo(orderCreateDTO.getAmount()) > 0) {
            throw new BusinessException("deposit cannot exceed order amount");
        }
        if (orderCreateDTO.getClueId() != null && orderCreateDTO.getClueId() <= 0) {
            throw new BusinessException("clueId must be greater than 0");
        }
        if (orderCreateDTO.getCustomerId() != null && orderCreateDTO.getCustomerId() <= 0) {
            throw new BusinessException("customerId must be greater than 0");
        }
    }

    private Clue getClueIfPresent(Long clueId) {
        if (clueId == null) {
            return null;
        }
        Clue clue = clueMapper.selectById(clueId);
        if (clue == null) {
            throw new BusinessException("clue not found");
        }
        return clue;
    }

    private Customer resolveCustomer(OrderCreateDTO orderCreateDTO, Clue clue) {
        Customer customer = null;
        if (orderCreateDTO.getCustomerId() != null) {
            customer = customerService.getByIdOrThrow(orderCreateDTO.getCustomerId());
        }
        if (clue == null) {
            return customer;
        }
        if (customer != null) {
            if (StringUtils.hasText(clue.getPhone())
                    && StringUtils.hasText(customer.getPhone())
                    && !customer.getPhone().equals(clue.getPhone())) {
                throw new BusinessException("clue phone does not match customer phone");
            }
            return customer;
        }
        return customerService.getOrCreateByClue(clue);
    }

    private BigDecimal defaultDeposit(OrderCreateDTO orderCreateDTO) {
        if (orderCreateDTO == null) {
            return BigDecimal.ZERO;
        }
        if (orderCreateDTO.getDeposit() != null) {
            return orderCreateDTO.getDeposit();
        }
        Integer normalizedType = OrderType.normalizeCode(orderCreateDTO.getType());
        if (normalizedType != null && normalizedType == OrderType.COUPON.getCode()) {
            return orderCreateDTO.getAmount();
        }
        return BigDecimal.ZERO;
    }

    private OrderStatus resolveInitialStatus(Order order) {
        return order.getDeposit() != null && order.getDeposit().compareTo(BigDecimal.ZERO) > 0
                ? OrderStatus.PAID_DEPOSIT
                : OrderStatus.CREATED;
    }

    private Order validateAndGetActionOrder(OrderActionDTO orderActionDTO, OrderStatus targetStatus) {
        validateOrderId(orderActionDTO == null ? null : orderActionDTO.getOrderId());
        Order order = getOrderById(orderActionDTO.getOrderId());
        ensureOrderCustomerBound(order);
        assertNextStatus(order, targetStatus);
        return order;
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException("orderId is required");
        }
    }

    private Order getOrderById(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        return order;
    }

    private void ensureOrderCustomerBound(Order order) {
        if (order.getCustomerId() != null) {
            customerService.getByIdOrThrow(order.getCustomerId());
            return;
        }
        if (order.getClueId() == null) {
            return;
        }
        Clue clue = getClueIfPresent(order.getClueId());
        Customer customer = customerService.getOrCreateByClue(clue);
        order.setCustomerId(customer.getId());
        inheritSource(order, clue, customer);
        markClueConverted(clue);
    }

    private void markClueConverted(Clue clue) {
        if (clue == null) {
            return;
        }
        clue.setStatus("converted");
        clue.setUpdatedAt(LocalDateTime.now());
        clueMapper.updateById(clue);
    }

    private void assertNextStatus(Order order, OrderStatus targetStatus) {
        OrderStatus currentStatus = getCurrentStatus(order);
        OrderStatus expectedNextStatus = currentStatus.nextNormalStatus();
        if (expectedNextStatus != targetStatus) {
            String expectedStatus = expectedNextStatus == null ? "no next status" : expectedNextStatus.name();
            throw new BusinessException("invalid order status transition: " + currentStatus.name()
                    + " -> " + targetStatus.name() + ", expected " + expectedStatus);
        }
    }

    private void ensurePlanOrderFinished(Long orderId) {
        PlanOrder planOrder = planOrderMapper.selectOne(new LambdaQueryWrapper<PlanOrder>()
                .eq(PlanOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (planOrder == null) {
            throw new BusinessException("plan order must be finished before order completion");
        }
        if (!PlanOrderStatus.FINISHED.name().equals(planOrder.getStatus())) {
            throw new BusinessException("plan order must be finished before order completion");
        }
    }

    private OrderStatus getCurrentStatus(Order order) {
        try {
            return OrderStatus.valueOf(order.getStatus());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("invalid order status: " + order.getStatus());
        }
    }

    private Order updateOrderStatus(Order order, OrderStatus targetStatus) {
        order.setStatus(targetStatus.name());
        order.setUpdateTime(LocalDateTime.now());
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException("failed to update order status");
        }
        refreshCustomerLifecycle(order.getCustomerId());
        log.info("order status updated, orderNo={}, customerId={}, status={}",
                order.getOrderNo(), order.getCustomerId(), order.getStatus());
        return order;
    }

    private void refreshCustomerLifecycle(Long customerId) {
        if (customerId != null) {
            customerService.refreshCustomerLifecycle(customerId);
        }
    }

    private void updateRemark(Order order, String remark) {
        if (StringUtils.hasText(remark)) {
            order.setRemark(remark);
        }
    }

    private void inheritSource(Order order, Clue clue, Customer customer) {
        if (clue != null) {
            order.setSourceChannel(SourceChannel.resolveCode(clue.getSourceChannel(), clue.getSource()));
            order.setSourceId(clue.getSourceId());
            return;
        }
        if (customer != null) {
            order.setSourceChannel(customer.getSourceChannel());
            order.setSourceId(customer.getSourceId());
        }
    }
}
