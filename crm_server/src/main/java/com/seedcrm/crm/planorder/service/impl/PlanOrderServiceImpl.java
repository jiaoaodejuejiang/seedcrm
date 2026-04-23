package com.seedcrm.crm.planorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.service.OrderSettlementService;
import com.seedcrm.crm.planorder.dto.PlanOrderActionDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderAssignRoleDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderCreateDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderDetailResponse;
import com.seedcrm.crm.planorder.dto.PlanOrderResponse;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.planorder.service.OrderRoleRecordService;
import com.seedcrm.crm.planorder.service.PlanOrderService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanOrderServiceImpl extends ServiceImpl<PlanOrderMapper, PlanOrder> implements PlanOrderService {

    private final PlanOrderMapper planOrderMapper;
    private final OrderMapper orderMapper;
    private final OrderRoleRecordService orderRoleRecordService;
    private final OrderSettlementService orderSettlementService;

    public PlanOrderServiceImpl(PlanOrderMapper planOrderMapper,
                                OrderMapper orderMapper,
                                OrderRoleRecordService orderRoleRecordService,
                                OrderSettlementService orderSettlementService) {
        this.planOrderMapper = planOrderMapper;
        this.orderMapper = orderMapper;
        this.orderRoleRecordService = orderRoleRecordService;
        this.orderSettlementService = orderSettlementService;
    }

    @Override
    @Transactional
    public PlanOrder createPlanOrder(PlanOrderCreateDTO planOrderCreateDTO) {
        Long orderId = planOrderCreateDTO == null ? null : planOrderCreateDTO.getOrderId();
        validateOrderId(orderId);
        Order order = getOrderOrThrow(orderId);
        ensureOrderCanCreatePlan(order);
        ensurePlanOrderNotExists(orderId);

        PlanOrder planOrder = new PlanOrder();
        planOrder.setOrderId(orderId);
        planOrder.setStatus(PlanOrderStatus.ARRIVED.name());
        planOrder.setCreateTime(LocalDateTime.now());
        if (planOrderMapper.insert(planOrder) <= 0) {
            throw new BusinessException("failed to create plan order");
        }
        return planOrder;
    }

    @Override
    @Transactional
    public PlanOrder arrive(PlanOrderActionDTO planOrderActionDTO) {
        PlanOrder planOrder = getPlanOrderForAction(planOrderActionDTO);
        ensureStatus(planOrder, PlanOrderStatus.ARRIVED);
        if (planOrder.getArriveTime() != null) {
            throw new BusinessException("plan order already arrived");
        }

        LocalDateTime now = LocalDateTime.now();
        planOrder.setArriveTime(now);
        updatePlanOrder(planOrder, "failed to update arrive time");

        Order order = getOrderOrThrow(planOrder.getOrderId());
        OrderStatus orderStatus = parseOrderStatus(order.getStatus());
        if (orderStatus == OrderStatus.CANCELLED || orderStatus == OrderStatus.REFUNDED) {
            throw new BusinessException("order cannot arrive from status " + orderStatus.name());
        }
        if (order.getArriveTime() == null) {
            order.setArriveTime(now);
        }
        if (orderStatus != OrderStatus.COMPLETED) {
            order.setStatus(OrderStatus.ARRIVED.name());
        }
        touchOrder(order, false);
        return planOrder;
    }

    @Override
    @Transactional
    public PlanOrder start(PlanOrderActionDTO planOrderActionDTO) {
        PlanOrder planOrder = getPlanOrderForAction(planOrderActionDTO);
        ensureStatus(planOrder, PlanOrderStatus.SERVICING);
        if (planOrder.getArriveTime() == null) {
            throw new BusinessException("plan order must arrive before start");
        }
        if (planOrder.getStartTime() != null) {
            throw new BusinessException("plan order already started");
        }

        planOrder.setStartTime(LocalDateTime.now());
        updatePlanOrder(planOrder, "failed to start plan order");

        Order order = getOrderOrThrow(planOrder.getOrderId());
        OrderStatus orderStatus = parseOrderStatus(order.getStatus());
        if (orderStatus == OrderStatus.CANCELLED || orderStatus == OrderStatus.REFUNDED) {
            throw new BusinessException("order cannot start service from status " + orderStatus.name());
        }
        if (orderStatus != OrderStatus.COMPLETED) {
            order.setStatus(OrderStatus.SERVING.name());
        }
        touchOrder(order, false);
        return planOrder;
    }

    @Override
    @Transactional
    public PlanOrder finish(PlanOrderActionDTO planOrderActionDTO) {
        PlanOrder planOrder = getPlanOrderForAction(planOrderActionDTO);
        ensureStatus(planOrder, PlanOrderStatus.FINISHED);
        if (planOrder.getStartTime() == null) {
            throw new BusinessException("plan order must start before finish");
        }
        if (planOrder.getFinishTime() != null) {
            throw new BusinessException("plan order already finished");
        }

        LocalDateTime now = LocalDateTime.now();
        planOrder.setFinishTime(now);
        updatePlanOrder(planOrder, "failed to finish plan order");

        Order order = getOrderOrThrow(planOrder.getOrderId());
        OrderStatus orderStatus = parseOrderStatus(order.getStatus());
        if (orderStatus == OrderStatus.CANCELLED || orderStatus == OrderStatus.REFUNDED) {
            throw new BusinessException("order cannot be completed from status " + orderStatus.name());
        }
        order.setStatus(OrderStatus.COMPLETED.name());
        order.setCompleteTime(now);
        if (planOrder.getArriveTime() != null && order.getArriveTime() == null) {
            order.setArriveTime(planOrder.getArriveTime());
        }
        touchOrder(order, true);
        orderSettlementService.settleCompletedOrder(order.getId());
        return planOrder;
    }

    @Override
    @Transactional
    public OrderRoleRecord assignRole(PlanOrderAssignRoleDTO planOrderAssignRoleDTO) {
        if (planOrderAssignRoleDTO == null) {
            throw new BusinessException("request body is required");
        }
        Long planOrderId = planOrderAssignRoleDTO == null ? null : planOrderAssignRoleDTO.getPlanOrderId();
        validatePlanOrderId(planOrderId);
        PlanOrder planOrder = getPlanOrderOrThrow(planOrderId);
        if (PlanOrderStatus.FINISHED.name().equals(planOrder.getStatus())) {
            throw new BusinessException("cannot assign role after plan order finished");
        }
        return orderRoleRecordService.assignRole(planOrderId,
                planOrderAssignRoleDTO.getRoleCode(),
                planOrderAssignRoleDTO.getUserId());
    }

    @Override
    public PlanOrderDetailResponse getDetail(Long planOrderId) {
        validatePlanOrderId(planOrderId);
        PlanOrder planOrder = getPlanOrderOrThrow(planOrderId);
        return new PlanOrderDetailResponse(PlanOrderResponse.from(planOrder),
                orderRoleRecordService.listByPlanOrderId(planOrderId));
    }

    private void ensurePlanOrderNotExists(Long orderId) {
        Long count = planOrderMapper.selectCount(new LambdaQueryWrapper<PlanOrder>()
                .eq(PlanOrder::getOrderId, orderId));
        if (count != null && count > 0) {
            throw new BusinessException("plan order already exists for order");
        }
    }

    private Order getOrderOrThrow(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        return order;
    }

    private void ensureOrderCanCreatePlan(Order order) {
        OrderStatus status = parseOrderStatus(order.getStatus());
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED || status == OrderStatus.COMPLETED) {
            throw new BusinessException("order status does not support plan order creation");
        }
        if (!status.isPaidStage()) {
            throw new BusinessException("order must be paid before plan order creation");
        }
    }

    private PlanOrder getPlanOrderForAction(PlanOrderActionDTO planOrderActionDTO) {
        Long planOrderId = planOrderActionDTO == null ? null : planOrderActionDTO.getPlanOrderId();
        validatePlanOrderId(planOrderId);
        return getPlanOrderOrThrow(planOrderId);
    }

    private PlanOrder getPlanOrderOrThrow(Long planOrderId) {
        PlanOrder planOrder = planOrderMapper.selectById(planOrderId);
        if (planOrder == null) {
            throw new BusinessException("plan order not found");
        }
        return planOrder;
    }

    private void updatePlanOrder(PlanOrder planOrder, String errorMessage) {
        if (planOrderMapper.updateById(planOrder) <= 0) {
            throw new BusinessException(errorMessage);
        }
    }

    private void ensureStatus(PlanOrder planOrder, PlanOrderStatus targetStatus) {
        PlanOrderStatus currentStatus = parsePlanOrderStatus(planOrder.getStatus());
        PlanOrderStatus expectedNextStatus = currentStatus.nextNormalStatus();
        if (targetStatus == PlanOrderStatus.SERVICING || targetStatus == PlanOrderStatus.FINISHED) {
            if (expectedNextStatus != targetStatus) {
                String expected = expectedNextStatus == null ? "no next status" : expectedNextStatus.name();
                throw new BusinessException("invalid plan order status transition: " + currentStatus.name()
                        + " -> " + targetStatus.name() + ", expected " + expected);
            }
            planOrder.setStatus(targetStatus.name());
            return;
        }
        if (currentStatus != targetStatus) {
            throw new BusinessException("invalid plan order status: " + currentStatus.name());
        }
    }

    private PlanOrderStatus parsePlanOrderStatus(String status) {
        try {
            return PlanOrderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("invalid plan order status: " + status);
        }
    }

    private OrderStatus parseOrderStatus(String status) {
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("invalid order status: " + status);
        }
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException("orderId is required");
        }
    }

    private void validatePlanOrderId(Long planOrderId) {
        if (planOrderId == null || planOrderId <= 0) {
            throw new BusinessException("planOrderId is required");
        }
    }

    private void touchOrder(Order order, boolean requireStatusUpdate) {
        order.setUpdateTime(LocalDateTime.now());
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException(requireStatusUpdate
                    ? "failed to complete order when finishing plan order"
                    : "failed to update order trace fields");
        }
    }
}
