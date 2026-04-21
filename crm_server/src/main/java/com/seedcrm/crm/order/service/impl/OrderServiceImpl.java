package com.seedcrm.crm.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.dto.OrderActionDTO;
import com.seedcrm.crm.order.dto.OrderAppointmentDTO;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.dto.OrderPayDTO;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.service.OrderService;
import com.seedcrm.crm.order.util.OrderNoGenerator;
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

    public OrderServiceImpl(OrderMapper orderMapper, ClueMapper clueMapper) {
        this.orderMapper = orderMapper;
        this.clueMapper = clueMapper;
    }

    @Override
    @Transactional
    public Order createOrder(OrderCreateDTO orderCreateDTO) {
        validateCreateRequest(orderCreateDTO);
        validateClue(orderCreateDTO.getClueId());

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.generate());
        order.setClueId(orderCreateDTO.getClueId());
        order.setCustomerId(orderCreateDTO.getCustomerId());
        order.setType(orderCreateDTO.getType());
        order.setAmount(orderCreateDTO.getAmount());
        order.setDeposit(defaultDeposit(orderCreateDTO.getDeposit()));
        order.setStatus(OrderStatus.CREATED.name());
        order.setRemark(orderCreateDTO.getRemark());
        order.setCreateTime(now);
        order.setUpdateTime(now);
        if (orderMapper.insert(order) <= 0) {
            throw new BusinessException("创建订单失败");
        }
        log.info("order created, orderNo={}, status={}", order.getOrderNo(), order.getStatus());
        return order;
    }

    @Override
    @Transactional
    public Order payDeposit(OrderPayDTO orderPayDTO) {
        validateOrderId(orderPayDTO == null ? null : orderPayDTO.getOrderId());
        Order order = getOrderById(orderPayDTO.getOrderId());
        assertNextStatus(order, OrderStatus.PAID_DEPOSIT);

        BigDecimal deposit = orderPayDTO.getDeposit() == null ? order.getDeposit() : orderPayDTO.getDeposit();
        if (deposit == null || deposit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("定金必须大于 0");
        }
        if (deposit.compareTo(order.getAmount()) > 0) {
            throw new BusinessException("定金不能大于订单总金额");
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
            throw new BusinessException("预约时间不能为空");
        }

        Order order = getOrderById(orderAppointmentDTO.getOrderId());
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
        Order order = validateAndGetActionOrder(orderActionDTO, OrderStatus.COMPLETED);
        order.setCompleteTime(LocalDateTime.now());
        updateRemark(order, orderActionDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.COMPLETED);
    }

    @Override
    @Transactional
    public Order cancel(OrderActionDTO orderActionDTO) {
        validateOrderId(orderActionDTO == null ? null : orderActionDTO.getOrderId());
        Order order = getOrderById(orderActionDTO.getOrderId());
        OrderStatus currentStatus = getCurrentStatus(order);
        if (!currentStatus.canCancel()) {
            throw new BusinessException("当前状态不允许取消订单: " + currentStatus.name());
        }
        updateRemark(order, orderActionDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.CANCELLED);
    }

    @Override
    @Transactional
    public Order refund(OrderActionDTO orderActionDTO) {
        validateOrderId(orderActionDTO == null ? null : orderActionDTO.getOrderId());
        Order order = getOrderById(orderActionDTO.getOrderId());
        OrderStatus currentStatus = getCurrentStatus(order);
        if (!currentStatus.canRefund()) {
            throw new BusinessException("当前状态不允许退款: " + currentStatus.name());
        }
        updateRemark(order, orderActionDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.REFUNDED);
    }

    private void validateCreateRequest(OrderCreateDTO orderCreateDTO) {
        if (orderCreateDTO == null) {
            throw new BusinessException("请求参数不能为空");
        }
        if (orderCreateDTO.getClueId() == null && orderCreateDTO.getCustomerId() == null) {
            throw new BusinessException("clueId 和 customerId 至少传一个");
        }
        if (!OrderType.isValid(orderCreateDTO.getType())) {
            throw new BusinessException("订单类型不正确");
        }
        if (orderCreateDTO.getAmount() == null || orderCreateDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("订单总金额必须大于 0");
        }
        if (orderCreateDTO.getDeposit() != null && orderCreateDTO.getDeposit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("定金不能小于 0");
        }
        if (orderCreateDTO.getDeposit() != null
                && orderCreateDTO.getDeposit().compareTo(orderCreateDTO.getAmount()) > 0) {
            throw new BusinessException("定金不能大于订单总金额");
        }
        if (orderCreateDTO.getClueId() != null && orderCreateDTO.getClueId() <= 0) {
            throw new BusinessException("clueId 必须大于 0");
        }
        if (orderCreateDTO.getCustomerId() != null && orderCreateDTO.getCustomerId() <= 0) {
            throw new BusinessException("customerId 必须大于 0");
        }
    }

    private void validateClue(Long clueId) {
        if (clueId == null) {
            return;
        }
        Clue clue = clueMapper.selectById(clueId);
        if (clue == null) {
            throw new BusinessException("线索不存在");
        }
    }

    private BigDecimal defaultDeposit(BigDecimal deposit) {
        return deposit == null ? BigDecimal.ZERO : deposit;
    }

    private Order validateAndGetActionOrder(OrderActionDTO orderActionDTO, OrderStatus targetStatus) {
        validateOrderId(orderActionDTO == null ? null : orderActionDTO.getOrderId());
        Order order = getOrderById(orderActionDTO.getOrderId());
        assertNextStatus(order, targetStatus);
        return order;
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new BusinessException("orderId 不能为空");
        }
    }

    private Order getOrderById(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    private void assertNextStatus(Order order, OrderStatus targetStatus) {
        OrderStatus currentStatus = getCurrentStatus(order);
        OrderStatus expectedNextStatus = currentStatus.nextNormalStatus();
        if (expectedNextStatus != targetStatus) {
            String expectedStatus = expectedNextStatus == null ? "无后续状态" : expectedNextStatus.name();
            throw new BusinessException(
                    "订单状态错误，当前状态为 " + currentStatus.name() + "，下一步只能流转到 " + expectedStatus);
        }
    }

    private OrderStatus getCurrentStatus(Order order) {
        try {
            return OrderStatus.valueOf(order.getStatus());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("订单状态非法: " + order.getStatus());
        }
    }

    private Order updateOrderStatus(Order order, OrderStatus targetStatus) {
        order.setStatus(targetStatus.name());
        order.setUpdateTime(LocalDateTime.now());
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException("订单状态更新失败");
        }
        log.info("order status updated, orderNo={}, status={}", order.getOrderNo(), order.getStatus());
        return order;
    }

    private void updateRemark(Order order, String remark) {
        if (StringUtils.hasText(remark)) {
            order.setRemark(remark);
        }
    }
}
