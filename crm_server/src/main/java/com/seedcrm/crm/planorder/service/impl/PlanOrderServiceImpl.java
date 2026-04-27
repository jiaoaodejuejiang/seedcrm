package com.seedcrm.crm.planorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.entity.OrderActionRecord;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderActionRecordMapper;
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
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.service.WecomTouchService;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PlanOrderServiceImpl extends ServiceImpl<PlanOrderMapper, PlanOrder> implements PlanOrderService {

    private static final Pattern CUSTOMER_SIGNATURE_PATTERN =
            Pattern.compile("\\\"customerSignature\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private static final Set<String> AUTO_ASSIGN_ROLE_CODES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");

    private final PlanOrderMapper planOrderMapper;
    private final OrderMapper orderMapper;
    private final OrderRoleRecordService orderRoleRecordService;
    private final OrderSettlementService orderSettlementService;
    private final WecomTouchService wecomTouchService;
    private final OrderActionRecordMapper orderActionRecordMapper;
    private final DbLockService dbLockService;

    public PlanOrderServiceImpl(PlanOrderMapper planOrderMapper,
                                OrderMapper orderMapper,
                                OrderRoleRecordService orderRoleRecordService,
                                OrderSettlementService orderSettlementService,
                                WecomTouchService wecomTouchService,
                                OrderActionRecordMapper orderActionRecordMapper,
                                DbLockService dbLockService) {
        this.planOrderMapper = planOrderMapper;
        this.orderMapper = orderMapper;
        this.orderRoleRecordService = orderRoleRecordService;
        this.orderSettlementService = orderSettlementService;
        this.wecomTouchService = wecomTouchService;
        this.orderActionRecordMapper = orderActionRecordMapper;
        this.dbLockService = dbLockService;
    }

    @Override
    @Transactional
    public PlanOrder createPlanOrder(PlanOrderCreateDTO planOrderCreateDTO) {
        return createPlanOrder(planOrderCreateDTO, null, null);
    }

    @Override
    @Transactional
    public PlanOrder createPlanOrder(PlanOrderCreateDTO planOrderCreateDTO, Long operatorUserId, String operatorRoleCode) {
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
        bindInitialRole(planOrder, operatorUserId, operatorRoleCode);
        return planOrder;
    }

    @Override
    @Transactional
    public PlanOrder arrive(PlanOrderActionDTO planOrderActionDTO) {
        PlanOrder planOrder = getPlanOrderForAction(planOrderActionDTO);
        ensureStatus(planOrder, PlanOrderStatus.ARRIVED);
        Order order = getOrderOrThrow(planOrder.getOrderId());
        ensureOrderVerifiedForService(order, "arrive");
        if (planOrder.getArriveTime() != null) {
            throw new BusinessException("plan order already arrived");
        }

        LocalDateTime now = LocalDateTime.now();
        planOrder.setArriveTime(now);
        updatePlanOrder(planOrder, "failed to update arrive time");

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
        Order order = getOrderOrThrow(planOrder.getOrderId());
        ensureOrderVerifiedForService(order, "start");
        if (planOrder.getArriveTime() == null) {
            throw new BusinessException("plan order must arrive before start");
        }
        if (planOrder.getStartTime() != null) {
            throw new BusinessException("plan order already started");
        }

        planOrder.setStartTime(LocalDateTime.now());
        updatePlanOrder(planOrder, "failed to start plan order");

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
        return finish(planOrderActionDTO, null);
    }

    @Override
    @Transactional
    public PlanOrder finish(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId) {
        PlanOrder planOrder = getPlanOrderForAction(planOrderActionDTO);
        ensureStatus(planOrder, PlanOrderStatus.FINISHED);
        Order order = dbLockService.lockOrder(planOrder.getOrderId());
        ensureOrderVerifiedForService(order, "finish");
        ensureServiceDetailSaved(order);
        if (planOrder.getStartTime() == null) {
            throw new BusinessException("plan order must start before finish");
        }
        if (planOrder.getFinishTime() != null) {
            throw new BusinessException("plan order already finished");
        }

        LocalDateTime now = LocalDateTime.now();
        planOrder.setFinishTime(now);
        updatePlanOrder(planOrder, "failed to finish plan order");

        OrderStatus orderStatus = parseOrderStatus(order.getStatus());
        if (orderStatus == OrderStatus.CANCELLED || orderStatus == OrderStatus.REFUNDED) {
            throw new BusinessException("order cannot be completed from status " + orderStatus.name());
        }
        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.COMPLETED.name());
        order.setCompleteTime(now);
        if (planOrder.getArriveTime() != null && order.getArriveTime() == null) {
            order.setArriveTime(planOrder.getArriveTime());
        }
        touchOrder(order, true);
        recordOrderAction(order.getId(), "SERVICE_FINISH", fromStatus, OrderStatus.COMPLETED.name(),
                operatorUserId, "服务完成");
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

    @Override
    @Transactional
    public WecomTouchLog sendServiceForm(Long planOrderId, String message) {
        validatePlanOrderId(planOrderId);
        PlanOrder planOrder = getPlanOrderOrThrow(planOrderId);
        Order order = getOrderOrThrow(planOrder.getOrderId());
        ensureOrderVerifiedForService(order, "send service form");
        ensureServiceDetailSaved(order);
        if (order.getCustomerId() == null || order.getCustomerId() <= 0) {
            throw new BusinessException("order customer is required");
        }
        String finalMessage = StringUtils.hasText(message) ? message.trim() : buildDefaultServiceFormMessage(order);
        return wecomTouchService.manualSend(order.getCustomerId(), finalMessage);
    }

    private void ensurePlanOrderNotExists(Long orderId) {
        Long count = planOrderMapper.selectCount(new LambdaQueryWrapper<PlanOrder>()
                .eq(PlanOrder::getOrderId, orderId));
        if (count != null && count > 0) {
            throw new BusinessException("plan order already exists for order");
        }
    }

    private void bindInitialRole(PlanOrder planOrder, Long operatorUserId, String operatorRoleCode) {
        if (planOrder == null || planOrder.getId() == null || operatorUserId == null || operatorUserId <= 0) {
            return;
        }
        if (!StringUtils.hasText(operatorRoleCode)) {
            return;
        }
        String normalizedRoleCode = operatorRoleCode.trim().toUpperCase(Locale.ROOT);
        if (!AUTO_ASSIGN_ROLE_CODES.contains(normalizedRoleCode)) {
            return;
        }
        orderRoleRecordService.assignRole(planOrder.getId(), normalizedRoleCode, operatorUserId);
    }

    private void ensureOrderVerifiedForService(Order order, String action) {
        if (order == null) {
            throw new BusinessException("order not found");
        }
        if (!"VERIFIED".equalsIgnoreCase(order.getVerificationStatus())) {
            throw new BusinessException("order must be verified before " + action);
        }
    }

    private Order getOrderOrThrow(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        return order;
    }

    private void ensureServiceDetailSaved(Order order) {
        String serviceDetailJson = order.getServiceDetailJson();
        if (!StringUtils.hasText(serviceDetailJson)) {
            throw new BusinessException("service form must be saved before finish");
        }
        Matcher matcher = CUSTOMER_SIGNATURE_PATTERN.matcher(serviceDetailJson);
        if (!matcher.find() || !StringUtils.hasText(matcher.group(1))) {
            throw new BusinessException("customer signature is required before finish");
        }
    }

    private String buildDefaultServiceFormMessage(Order order) {
        String orderNo = StringUtils.hasText(order.getOrderNo()) ? order.getOrderNo().trim() : "--";
        String appointment = order.getAppointmentTime() == null ? "待确认" : order.getAppointmentTime().toString().replace('T', ' ');
        return "您好，您的服务确认单已更新。订单号：" + orderNo
                + "，预约时间：" + appointment
                + "。如需调整，请直接联系门店客服。";
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

    private void recordOrderAction(Long orderId,
                                   String actionType,
                                   String fromStatus,
                                   String toStatus,
                                   Long operatorUserId,
                                   String remark) {
        if (orderId == null || orderId <= 0) {
            return;
        }
        OrderActionRecord record = new OrderActionRecord();
        record.setOrderId(orderId);
        record.setActionType(actionType);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setOperatorUserId(operatorUserId);
        record.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        record.setCreateTime(LocalDateTime.now());
        orderActionRecordMapper.insert(record);
    }
}
