package com.seedcrm.crm.planorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.entity.OrderActionRecord;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderActionRecordMapper;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.service.OrderSettlementService;
import com.seedcrm.crm.order.support.ServiceFormVersionSupport;
import com.seedcrm.crm.planorder.dto.PlanOrderActionDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderAssignRoleDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderCreateDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderDetailResponse;
import com.seedcrm.crm.planorder.dto.PlanOrderResponse;
import com.seedcrm.crm.planorder.dto.PlanOrderServiceFormStateResponse;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.planorder.service.OrderRoleRecordService;
import com.seedcrm.crm.planorder.service.PlanOrderService;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import com.seedcrm.crm.scheduler.service.SchedulerOutboxService;
import com.seedcrm.crm.systemflow.support.SystemFlowRuntimeBridge;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.service.WecomTouchService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PlanOrderServiceImpl extends ServiceImpl<PlanOrderMapper, PlanOrder> implements PlanOrderService {

    private static final Set<String> AUTO_ASSIGN_ROLE_CODES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");
    private static final String SERVICE_FORM_CONFIRM_STATUS = ServiceFormVersionSupport.CONFIRM_STATUS;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlanOrderMapper planOrderMapper;
    private final OrderMapper orderMapper;
    private final OrderRoleRecordService orderRoleRecordService;
    private final OrderSettlementService orderSettlementService;
    private final WecomTouchService wecomTouchService;
    private final OrderActionRecordMapper orderActionRecordMapper;
    private final DbLockService dbLockService;
    private final SchedulerOutboxService schedulerOutboxService;
    private final SystemFlowRuntimeBridge systemFlowRuntimeBridge;
    private final ObjectMapper objectMapper;

    public PlanOrderServiceImpl(PlanOrderMapper planOrderMapper,
                                OrderMapper orderMapper,
                                OrderRoleRecordService orderRoleRecordService,
                                 OrderSettlementService orderSettlementService,
                                 WecomTouchService wecomTouchService,
                                 OrderActionRecordMapper orderActionRecordMapper,
                                 DbLockService dbLockService,
                                 SchedulerOutboxService schedulerOutboxService,
                                 SystemFlowRuntimeBridge systemFlowRuntimeBridge,
                                 ObjectMapper objectMapper) {
        this.planOrderMapper = planOrderMapper;
        this.orderMapper = orderMapper;
        this.orderRoleRecordService = orderRoleRecordService;
        this.orderSettlementService = orderSettlementService;
        this.wecomTouchService = wecomTouchService;
        this.orderActionRecordMapper = orderActionRecordMapper;
        this.dbLockService = dbLockService;
        this.schedulerOutboxService = schedulerOutboxService;
        this.systemFlowRuntimeBridge = systemFlowRuntimeBridge;
        this.objectMapper = objectMapper;
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
        LocalDateTime now = LocalDateTime.now();
        boolean completedCompatibilityOrder = parseOrderStatus(order.getStatus()) == OrderStatus.COMPLETED;
        planOrder.setOrderId(orderId);
        planOrder.setStatus(completedCompatibilityOrder ? PlanOrderStatus.FINISHED.name() : PlanOrderStatus.ARRIVED.name());
        planOrder.setCreateTime(now);
        if (completedCompatibilityOrder) {
            LocalDateTime finishTime = order.getCompleteTime() == null ? now : order.getCompleteTime();
            LocalDateTime arriveTime = order.getArriveTime() == null ? firstTime(order.getAppointmentTime(), finishTime) : order.getArriveTime();
            planOrder.setArriveTime(arriveTime);
            planOrder.setStartTime(firstTime(arriveTime, finishTime));
            planOrder.setFinishTime(finishTime);
        }
        if (planOrderMapper.insert(planOrder) <= 0) {
            throw new BusinessException("failed to create plan order");
        }
        bindInitialRole(planOrder, operatorUserId, operatorRoleCode);
        systemFlowRuntimeBridge.recordOrderAction(order, "VERIFY", "PLAN_CREATE",
                operatorUserId, operatorRoleCode, "创建服务计划单");
        if (!completedCompatibilityOrder) {
            systemFlowRuntimeBridge.recordOrderAction(order, "PLAN_CREATED", "PLAN_ARRIVE",
                    operatorUserId, operatorRoleCode, "进入到店服务准备");
        }
        return planOrder;
    }

    @Override
    @Transactional
    public PlanOrder arrive(PlanOrderActionDTO planOrderActionDTO) {
        return arrive(planOrderActionDTO, null, null);
    }

    @Override
    @Transactional
    public PlanOrder arrive(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId, String operatorRoleCode) {
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
        systemFlowRuntimeBridge.recordOrderAction(order, "PLAN_CREATED", "PLAN_ARRIVE",
                operatorUserId, operatorRoleCode, "确认到店");
        return planOrder;
    }

    @Override
    @Transactional
    public PlanOrder start(PlanOrderActionDTO planOrderActionDTO) {
        return start(planOrderActionDTO, null, null);
    }

    @Override
    @Transactional
    public PlanOrder start(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId, String operatorRoleCode) {
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
        ensureServiceFormConfirmed(order, "start");

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
        systemFlowRuntimeBridge.recordOrderAction(order, "SERVICE_FORM_CONFIRMED", "PLAN_START",
                operatorUserId, operatorRoleCode, "开始服务");
        return planOrder;
    }

    @Override
    @Transactional
    public PlanOrder printServiceForm(PlanOrderActionDTO planOrderActionDTO) {
        return printServiceForm(planOrderActionDTO, null, null);
    }

    @Override
    @Transactional
    public PlanOrder printServiceForm(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId, String operatorRoleCode) {
        PlanOrder planOrder = getPlanOrderForAction(planOrderActionDTO);
        if (PlanOrderStatus.FINISHED.name().equals(planOrder.getStatus())) {
            throw new BusinessException("cannot print service form after plan order finished");
        }
        Order order = dbLockService.lockOrder(planOrder.getOrderId());
        ensureOrderVerifiedForService(order, "print service form");
        ensureServiceDetailSaved(order);

        ObjectNode root = ServiceFormVersionSupport.parseRoot(order.getServiceDetailJson(), objectMapper);
        String currentHash = ServiceFormVersionSupport.printableHash(root, objectMapper);
        ServiceFormVersionSupport.clearConfirmationIfHashMismatch(root, currentHash);
        ObjectNode printAudit = buildPrintAudit(root, planOrder.getId(), currentHash, operatorUserId, operatorRoleCode);
        root.set("printAudit", printAudit);

        order.setServiceDetailJson(writeServiceDetailJson(root));
        touchOrder(order, false);
        String extraJson = buildServiceFormActionExtra(planOrder.getId(), currentHash, operatorRoleCode, printAudit);
        recordOrderAction(order.getId(), "SERVICE_FORM_PRINT", order.getStatus(), order.getStatus(),
                operatorUserId, "service confirmation form printed", extraJson);
        return planOrder;
    }

    @Override
    @Transactional
    public PlanOrder confirmServiceForm(PlanOrderActionDTO planOrderActionDTO) {
        return confirmServiceForm(planOrderActionDTO, null, null);
    }

    @Override
    @Transactional
    public PlanOrder confirmServiceForm(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId, String operatorRoleCode) {
        PlanOrder planOrder = getPlanOrderForAction(planOrderActionDTO);
        if (PlanOrderStatus.FINISHED.name().equals(planOrder.getStatus())) {
            throw new BusinessException("cannot confirm service form after plan order finished");
        }
        Order order = dbLockService.lockOrder(planOrder.getOrderId());
        ensureOrderVerifiedForService(order, "confirm service form");
        ensureServiceDetailSaved(order);
        ObjectNode root = ServiceFormVersionSupport.parseRoot(order.getServiceDetailJson(), objectMapper);
        String currentHash = ServiceFormVersionSupport.printableHash(root, objectMapper);
        if (isServiceFormConfirmed(root)) {
            if (!ServiceFormVersionSupport.hasCurrentConfirmation(root, currentHash)) {
                throw new BusinessException("service form content changed; print current version before confirming");
            }
            return planOrder;
        }
        if (!ServiceFormVersionSupport.hasCurrentPrintAudit(root, currentHash)) {
            throw new BusinessException("please print current service form version before confirming");
        }
        order.setServiceDetailJson(markServiceFormConfirmed(root, currentHash, operatorUserId, operatorRoleCode));
        touchOrder(order, false);
        String extraJson = buildServiceFormActionExtra(planOrder.getId(), currentHash, operatorRoleCode, root.path("printAudit"));
        recordOrderAction(order.getId(), "SERVICE_FORM_CONFIRM", order.getStatus(), order.getStatus(),
                operatorUserId, "paper service confirmation form signed", extraJson);
        systemFlowRuntimeBridge.recordOrderAction(order, "PLAN_ARRIVED", "SERVICE_FORM_CONFIRM",
                operatorUserId, operatorRoleCode, "纸质确认单已确认");
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
        return finish(planOrderActionDTO, operatorUserId, null);
    }

    @Override
    @Transactional
    public PlanOrder finish(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId, String operatorRoleCode) {
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
        SchedulerOutboxEvent outboxEvent = schedulerOutboxService.enqueueFulfillmentEvent(order, planOrder, "crm.order.used");
        if (requiresDistributionOutbox(order) && outboxEvent == null) {
            throw new BusinessException("distribution fulfillment outbox event is required");
        }
        systemFlowRuntimeBridge.recordOrderAction(order, "PLAN_SERVICING", "PLAN_FINISH",
                operatorUserId, operatorRoleCode, "完成服务");
        systemFlowRuntimeBridge.recordOrderAction(order, "PLAN_FINISHED", "ORDER_COMPLETE",
                operatorUserId, operatorRoleCode, "订单完成");
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
    public PlanOrderServiceFormStateResponse getServiceFormState(Long planOrderId) {
        validatePlanOrderId(planOrderId);
        PlanOrder planOrder = getPlanOrderOrThrow(planOrderId);
        Order order = getOrderOrThrow(planOrder.getOrderId());
        return buildServiceFormState(planOrder, order);
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
    }

    private void ensureServiceFormConfirmed(Order order, String action) {
        ensureServiceDetailSaved(order);
        ObjectNode root = ServiceFormVersionSupport.parseRoot(order.getServiceDetailJson(), objectMapper);
        String currentHash = ServiceFormVersionSupport.printableHash(root, objectMapper);
        if (!isServiceFormConfirmed(root)) {
            throw new BusinessException("service form must be printed and confirmed before " + action);
        }
        if (!ServiceFormVersionSupport.hasCurrentConfirmation(root, currentHash)) {
            throw new BusinessException("service form content changed; print current version before " + action);
        }
    }

    private PlanOrderServiceFormStateResponse buildServiceFormState(PlanOrder planOrder, Order order) {
        PlanOrderServiceFormStateResponse response = new PlanOrderServiceFormStateResponse();
        response.setPlanOrderId(planOrder == null ? null : planOrder.getId());
        response.setOrderId(order == null ? null : order.getId());
        response.setProjectionVersion(ServiceFormVersionSupport.PROJECTION_VERSION);
        if (order == null || !StringUtils.hasText(order.getServiceDetailJson())) {
            return response;
        }
        response.setSaved(true);
        ObjectNode root = ServiceFormVersionSupport.parseRoot(order.getServiceDetailJson(), objectMapper);
        String currentHash = ServiceFormVersionSupport.printableHash(root, objectMapper);
        response.setServiceDetailHash(currentHash);

        JsonNode printAudit = root.path("printAudit");
        response.setPrintStatus(textValue(printAudit, "status"));
        response.setPrintCount(printAudit.path("printCount").asInt(0));
        response.setPrintedAt(textValue(printAudit, "printedAt"));
        response.setPrintedByUserId(longValue(printAudit, "printedByUserId"));
        response.setPrintedByRoleCode(textValue(printAudit, "printedByRoleCode"));
        boolean printedCurrentVersion = ServiceFormVersionSupport.hasCurrentPrintAudit(root, currentHash);
        response.setPrinted(printedCurrentVersion);
        boolean stale = ServiceFormVersionSupport.PRINT_STATUS_STALE.equalsIgnoreCase(response.getPrintStatus())
                || (StringUtils.hasText(response.getPrintStatus()) && !printedCurrentVersion);
        response.setStale(stale);
        response.setStaleReason(textValue(printAudit, "staleReason"));

        JsonNode confirmation = root.path("confirmation");
        response.setConfirmationStatus(textValue(confirmation, "status"));
        response.setConfirmedAt(textValue(confirmation, "confirmedAt"));
        response.setConfirmedByUserId(longValue(confirmation, "confirmedByUserId"));
        response.setConfirmedByRoleCode(textValue(confirmation, "confirmedByRoleCode"));
        response.setConfirmed(ServiceFormVersionSupport.hasCurrentConfirmation(root, currentHash));
        return response;
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(fieldName).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private Long longValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.path(fieldName).canConvertToLong()) {
            return null;
        }
        return node.path(fieldName).asLong();
    }

    private boolean isServiceFormConfirmed(String serviceDetailJson) {
        if (!StringUtils.hasText(serviceDetailJson)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(serviceDetailJson);
            String nestedStatus = root.path("confirmation").path("status").asText("");
            String flatStatus = root.path("serviceFormStatus").asText("");
            return SERVICE_FORM_CONFIRM_STATUS.equalsIgnoreCase(nestedStatus)
                    || SERVICE_FORM_CONFIRM_STATUS.equalsIgnoreCase(flatStatus);
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isServiceFormConfirmed(ObjectNode root) {
        if (root == null) {
            return false;
        }
        String nestedStatus = root.path("confirmation").path("status").asText("");
        String flatStatus = root.path("serviceFormStatus").asText("");
        return SERVICE_FORM_CONFIRM_STATUS.equalsIgnoreCase(nestedStatus)
                || SERVICE_FORM_CONFIRM_STATUS.equalsIgnoreCase(flatStatus);
    }

    private String markServiceFormConfirmed(ObjectNode root, String serviceDetailHash, Long operatorUserId, String operatorRoleCode) {
        try {
            ObjectNode confirmation = objectMapper.createObjectNode();
            confirmation.put("status", SERVICE_FORM_CONFIRM_STATUS);
            confirmation.put("signatureMode", "PAPER");
            confirmation.put("signatureRequired", true);
            confirmation.put("confirmedAt", DATE_TIME_FORMATTER.format(LocalDateTime.now()));
            confirmation.put("serviceDetailHash", serviceDetailHash);
            confirmation.put("projectionVersion", ServiceFormVersionSupport.PROJECTION_VERSION);
            if (operatorUserId != null && operatorUserId > 0) {
                confirmation.put("confirmedByUserId", operatorUserId);
            }
            if (StringUtils.hasText(operatorRoleCode)) {
                confirmation.put("confirmedByRoleCode", operatorRoleCode.trim());
            }
            if (root.has("printAudit")) {
                confirmation.set("printAudit", root.get("printAudit").deepCopy());
            }
            root.put("serviceFormStatus", SERVICE_FORM_CONFIRM_STATUS);
            root.set("confirmation", confirmation);
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new BusinessException("service form json is invalid");
        }
    }

    private ObjectNode buildPrintAudit(ObjectNode root,
                                       Long planOrderId,
                                       String serviceDetailHash,
                                       Long operatorUserId,
                                       String operatorRoleCode) {
        JsonNode previousAudit = root == null ? null : root.path("printAudit");
        int printCount = previousAudit == null || previousAudit.isMissingNode()
                ? 1
                : previousAudit.path("printCount").asInt(0) + 1;
        ObjectNode printAudit = objectMapper.createObjectNode();
        printAudit.put("status", ServiceFormVersionSupport.PRINT_STATUS_PRINTED);
        printAudit.put("printedAt", DATE_TIME_FORMATTER.format(LocalDateTime.now()));
        printAudit.put("serviceDetailHash", serviceDetailHash);
        printAudit.put("projectionVersion", ServiceFormVersionSupport.PROJECTION_VERSION);
        printAudit.put("printCount", printCount);
        if (planOrderId != null && planOrderId > 0) {
            printAudit.put("planOrderId", planOrderId);
        }
        if (operatorUserId != null && operatorUserId > 0) {
            printAudit.put("printedByUserId", operatorUserId);
        }
        if (StringUtils.hasText(operatorRoleCode)) {
            printAudit.put("printedByRoleCode", operatorRoleCode.trim());
        }
        return printAudit;
    }

    private String buildServiceFormActionExtra(Long planOrderId,
                                               String serviceDetailHash,
                                               String operatorRoleCode,
                                               JsonNode printAudit) {
        ObjectNode extra = objectMapper.createObjectNode();
        if (planOrderId != null && planOrderId > 0) {
            extra.put("planOrderId", planOrderId);
        }
        if (StringUtils.hasText(serviceDetailHash)) {
            extra.put("serviceDetailHash", serviceDetailHash);
        }
        extra.put("projectionVersion", ServiceFormVersionSupport.PROJECTION_VERSION);
        if (StringUtils.hasText(operatorRoleCode)) {
            extra.put("operatorRoleCode", operatorRoleCode.trim());
        }
        if (printAudit != null && !printAudit.isMissingNode() && !printAudit.isNull()) {
            extra.set("printAudit", printAudit.deepCopy());
        }
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (Exception exception) {
            return null;
        }
    }

    private String writeServiceDetailJson(JsonNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new BusinessException("service form json is invalid");
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
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED) {
            throw new BusinessException("order status does not support plan order creation");
        }
        if (status == OrderStatus.COMPLETED) {
            return;
        }
        if (!status.isPaidStage()) {
            throw new BusinessException("order must be paid before plan order creation");
        }
    }

    private LocalDateTime firstTime(LocalDateTime preferred, LocalDateTime fallback) {
        return preferred == null ? fallback : preferred;
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

    private boolean requiresDistributionOutbox(Order order) {
        return order != null
                && ("distribution".equalsIgnoreCase(order.getSource())
                || StringUtils.hasText(order.getExternalPartnerCode())
                || StringUtils.hasText(order.getExternalOrderId()));
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
        recordOrderAction(orderId, actionType, fromStatus, toStatus, operatorUserId, remark, null);
    }

    private void recordOrderAction(Long orderId,
                                   String actionType,
                                   String fromStatus,
                                   String toStatus,
                                   Long operatorUserId,
                                   String remark,
                                   String extraJson) {
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
        record.setExtraJson(StringUtils.hasText(extraJson) ? extraJson : null);
        record.setCreateTime(LocalDateTime.now());
        orderActionRecordMapper.insert(record);
    }
}
