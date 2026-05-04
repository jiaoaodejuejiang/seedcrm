package com.seedcrm.crm.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.seedcrm.crm.order.dto.OrderServiceDetailDTO;
import com.seedcrm.crm.order.dto.OrderVoucherVerifyDTO;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.entity.OrderActionRecord;
import com.seedcrm.crm.order.entity.OrderRefundRecord;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.order.mapper.OrderActionRecordMapper;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.mapper.OrderRefundRecordMapper;
import com.seedcrm.crm.order.service.OrderSettlementService;
import com.seedcrm.crm.order.service.OrderService;
import com.seedcrm.crm.order.service.OrderVoucherVerificationGateway;
import com.seedcrm.crm.order.service.OrderVoucherVerificationResult;
import com.seedcrm.crm.order.support.OrderAmountMaskingSupport;
import com.seedcrm.crm.order.support.ServiceFormVersionSupport;
import com.seedcrm.crm.order.util.OrderNoGenerator;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import com.seedcrm.crm.systemflow.support.SystemFlowRuntimeBridge;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final String REFUND_SCENE_STORE_SERVICE = "STORE_SERVICE";
    private static final String REFUND_SCENE_FINANCE_PAYMENT = "FINANCE_VERIFIED_PAYMENT";
    private static final String CONFIG_DEPOSIT_DIRECT_ENABLED = "deposit.direct.enabled";
    private static final String CONFIG_SERVICE_AMOUNT_EDIT_ROLES = "amount.visibility.service_confirm_edit_roles";
    private static final String DEFAULT_SERVICE_AMOUNT_EDIT_ROLES = "ADMIN,FINANCE";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ACTION_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> ACTIVE_APPOINTMENT_ACTIONS = Set.of(
            "APPOINTMENT_CREATE",
            "APPOINTMENT_CHANGE");
    private static final Set<String> ACTIVE_APPOINTMENT_ORDER_STATUSES = Set.of(
            OrderStatus.APPOINTMENT.name(),
            OrderStatus.ARRIVED.name(),
            OrderStatus.SERVING.name());
    private static final Set<String> STORE_PERFORMANCE_ROLES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");
    private static final Set<String> FINANCE_CUSTOMER_SERVICE_REVERSE_ROLES = Set.of(
            "ONLINE_CUSTOMER_SERVICE",
            "CLUE_MANAGER",
            "CONSULTANT");

    private record AppointmentActionSnapshot(List<LocalDateTime> slots, Integer headcount) {
    }

    private final OrderMapper orderMapper;
    private final ClueMapper clueMapper;
    private final CustomerService customerService;
    private final CustomerTagService customerTagService;
    private final PlanOrderMapper planOrderMapper;
    private final DistributorIncomeService distributorIncomeService;
    private final DbLockService dbLockService;
    private final OrderSettlementService orderSettlementService;
    private final OrderVoucherVerificationGateway voucherVerificationGateway;
    private final OrderActionRecordMapper orderActionRecordMapper;
    private final OrderRefundRecordMapper orderRefundRecordMapper;
    private final SalaryDetailMapper salaryDetailMapper;
    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final SystemFlowRuntimeBridge systemFlowRuntimeBridge;
    private final TransactionTemplate voucherAuditTransactionTemplate;

    public OrderServiceImpl(OrderMapper orderMapper,
                            ClueMapper clueMapper,
                            CustomerService customerService,
                            CustomerTagService customerTagService,
                            PlanOrderMapper planOrderMapper,
                            DistributorIncomeService distributorIncomeService,
                            DbLockService dbLockService,
                            OrderSettlementService orderSettlementService,
                            OrderVoucherVerificationGateway voucherVerificationGateway,
                            OrderActionRecordMapper orderActionRecordMapper,
                            OrderRefundRecordMapper orderRefundRecordMapper,
                            SalaryDetailMapper salaryDetailMapper,
                            ObjectMapper objectMapper,
                            SystemConfigService systemConfigService,
                            SystemFlowRuntimeBridge systemFlowRuntimeBridge) {
        this(orderMapper, clueMapper, customerService, customerTagService, planOrderMapper, distributorIncomeService,
                dbLockService, orderSettlementService, voucherVerificationGateway, orderActionRecordMapper,
                orderRefundRecordMapper, salaryDetailMapper, objectMapper, systemConfigService, systemFlowRuntimeBridge, null);
    }

    @Autowired
    public OrderServiceImpl(OrderMapper orderMapper,
                            ClueMapper clueMapper,
                            CustomerService customerService,
                            CustomerTagService customerTagService,
                            PlanOrderMapper planOrderMapper,
                            DistributorIncomeService distributorIncomeService,
                            DbLockService dbLockService,
                            OrderSettlementService orderSettlementService,
                            OrderVoucherVerificationGateway voucherVerificationGateway,
                            OrderActionRecordMapper orderActionRecordMapper,
                            OrderRefundRecordMapper orderRefundRecordMapper,
                            SalaryDetailMapper salaryDetailMapper,
                            ObjectMapper objectMapper,
                            SystemConfigService systemConfigService,
                            SystemFlowRuntimeBridge systemFlowRuntimeBridge,
                            PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.clueMapper = clueMapper;
        this.customerService = customerService;
        this.customerTagService = customerTagService;
        this.planOrderMapper = planOrderMapper;
        this.distributorIncomeService = distributorIncomeService;
        this.dbLockService = dbLockService;
        this.orderSettlementService = orderSettlementService;
        this.voucherVerificationGateway = voucherVerificationGateway;
        this.orderActionRecordMapper = orderActionRecordMapper;
        this.orderRefundRecordMapper = orderRefundRecordMapper;
        this.salaryDetailMapper = salaryDetailMapper;
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
        this.systemFlowRuntimeBridge = systemFlowRuntimeBridge;
        this.voucherAuditTransactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        if (this.voucherAuditTransactionTemplate != null) {
            this.voucherAuditTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
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
        return appointment(orderAppointmentDTO, null, null);
    }

    @Override
    @Transactional
    public Order appointment(OrderAppointmentDTO orderAppointmentDTO, Long operatorUserId, String operatorRoleCode) {
        validateOrderId(orderAppointmentDTO == null ? null : orderAppointmentDTO.getOrderId());
        List<LocalDateTime> nextAppointmentSlots = normalizeAppointmentSlots(orderAppointmentDTO);
        LocalDateTime nextAppointmentTime = nextAppointmentSlots.get(0);
        Integer nextHeadcount = resolveAppointmentHeadcount(
                orderAppointmentDTO == null ? null : orderAppointmentDTO.getHeadcount(),
                nextAppointmentSlots.size());

        Order order = dbLockService.lockOrder(orderAppointmentDTO.getOrderId());
        ensureOrderCustomerBound(order);
        OrderStatus currentStatus = getCurrentStatus(order);
        AppointmentActionSnapshot previousSnapshot = currentStatus == OrderStatus.APPOINTMENT
                ? resolveCurrentAppointmentSnapshot(order)
                : new AppointmentActionSnapshot(List.of(), 0);
        LocalDateTime previousAppointmentTime = previousSnapshot.slots().isEmpty()
                ? order.getAppointmentTime()
                : previousSnapshot.slots().get(0);
        String previousStoreName = firstText(order.getAppointmentStoreName(), orderAppointmentDTO.getPreviousStoreName());
        String nextStoreName = firstText(orderAppointmentDTO.getStoreName(), previousStoreName);
        if (!StringUtils.hasText(nextStoreName)) {
            throw new BusinessException("appointment storeName is required");
        }
        assertAppointmentSlotsAvailable(order.getId(), nextStoreName, nextAppointmentSlots);
        if (currentStatus != OrderStatus.APPOINTMENT) {
            assertNextStatus(order, OrderStatus.APPOINTMENT);
        }
        order.setAppointmentTime(nextAppointmentTime);
        order.setAppointmentStoreName(nextStoreName);
        updateRemark(order, orderAppointmentDTO.getRemark());
        Order updated = updateOrderStatus(order, OrderStatus.APPOINTMENT);
        boolean reschedule = currentStatus == OrderStatus.APPOINTMENT;
        recordOrderAction(updated.getId(),
                reschedule ? "APPOINTMENT_CHANGE" : "APPOINTMENT_CREATE",
                currentStatus.name(),
                OrderStatus.APPOINTMENT.name(),
                operatorUserId,
                reschedule ? "更改预约档期" : "预约排档",
                buildAppointmentActionExtra(
                        previousAppointmentTime,
                        nextAppointmentTime,
                        previousSnapshot.slots(),
                        nextAppointmentSlots,
                        previousSnapshot.headcount(),
                        nextHeadcount,
                        previousStoreName,
                        nextStoreName,
                        orderAppointmentDTO.getRemark(),
                        resolveAppointmentReasonType(
                                orderAppointmentDTO.getAppointmentReasonType(),
                                reschedule ? "RESCHEDULE" : "CUSTOMER_REQUEST"),
                        operatorRoleCode,
                        resolveAppointmentSourceSurface(orderAppointmentDTO.getSourceSurface())));
        systemFlowRuntimeBridge.recordOrderAction(updated, "ORDER_PAID", "ORDER_APPOINTMENT",
                operatorUserId, operatorRoleCode, reschedule ? "更改预约档期" : "预约门店档期");
        return updated;
    }

    @Override
    @Transactional
    public Order cancelAppointment(OrderActionDTO orderActionDTO) {
        return cancelAppointment(orderActionDTO, null, null);
    }

    @Override
    @Transactional
    public Order cancelAppointment(OrderActionDTO orderActionDTO, Long operatorUserId, String operatorRoleCode) {
        validateOrderId(orderActionDTO == null ? null : orderActionDTO.getOrderId());
        Order order = dbLockService.lockOrder(orderActionDTO.getOrderId());
        ensureOrderCustomerBound(order);
        if (getCurrentStatus(order) != OrderStatus.APPOINTMENT) {
            throw new BusinessException("only appointment order can cancel appointment");
        }
        AppointmentActionSnapshot previousSnapshot = resolveCurrentAppointmentSnapshot(order);
        LocalDateTime previousAppointmentTime = previousSnapshot.slots().isEmpty()
                ? order.getAppointmentTime()
                : previousSnapshot.slots().get(0);
        String previousStoreName = order.getAppointmentStoreName();
        order.setAppointmentTime(null);
        order.setAppointmentStoreName(null);
        updateRemark(order, orderActionDTO.getRemark());
        Order updated = updateOrderStatus(order, OrderStatus.PAID_DEPOSIT);
        recordOrderAction(updated.getId(),
                "APPOINTMENT_CANCEL",
                OrderStatus.APPOINTMENT.name(),
                OrderStatus.PAID_DEPOSIT.name(),
                operatorUserId,
                "取消预约排档",
                buildAppointmentActionExtra(
                        previousAppointmentTime,
                        null,
                        previousSnapshot.slots(),
                        List.of(),
                        previousSnapshot.headcount(),
                        0,
                        previousStoreName,
                        null,
                        orderActionDTO == null ? null : orderActionDTO.getRemark(),
                        resolveAppointmentReasonType(
                                orderActionDTO == null ? null : orderActionDTO.getAppointmentReasonType(),
                                "CUSTOMER_CANCEL"),
                        operatorRoleCode,
                        resolveAppointmentSourceSurface(orderActionDTO == null ? null : orderActionDTO.getSourceSurface())));
        systemFlowRuntimeBridge.recordOrderAction(updated, "APPOINTMENT", "ORDER_APPOINTMENT_CANCEL",
                operatorUserId, operatorRoleCode, "取消预约排档");
        return updated;
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
        return complete(orderActionDTO, null);
    }

    @Override
    @Transactional
    public Order complete(OrderActionDTO orderActionDTO, Long operatorUserId) {
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
        String fromStatus = order.getStatus();
        updateOrderStatus(order, OrderStatus.COMPLETED);
        recordOrderAction(order.getId(), "ORDER_COMPLETE", fromStatus, OrderStatus.COMPLETED.name(),
                operatorUserId, orderActionDTO.getRemark());
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
        return refund(orderActionDTO, null);
    }

    @Override
    @Transactional
    public Order refund(OrderActionDTO orderActionDTO, Long operatorUserId) {
        validateOrderId(orderActionDTO == null ? null : orderActionDTO.getOrderId());
        Order order = dbLockService.lockOrder(orderActionDTO.getOrderId());
        ensureOrderCustomerBound(order);
        OrderRefundRecord existingRefund = findSameRefund(order, orderActionDTO);
        if (existingRefund != null) {
            attachRefundResult(order, existingRefund, true);
            return order;
        }
        OrderStatus currentStatus = getCurrentStatus(order);
        if (!currentStatus.canRefund()) {
            throw new BusinessException("order cannot be refunded from status " + currentStatus.name());
        }
        validateRefundRequest(order, orderActionDTO);
        String refundRemark = buildRefundRemark(orderActionDTO);
        RefundRegistration registration = createRefundRecord(order, orderActionDTO, operatorUserId);
        if (registration.duplicate()) {
            attachRefundResult(order, registration.record(), true);
            return order;
        }
        OrderRefundRecord refundRecord = registration.record();
        createSalaryReversalDetails(order, orderActionDTO, refundRecord);
        updateRemark(order, refundRemark);
        if (currentStatus == OrderStatus.COMPLETED) {
            order.setUpdateTime(LocalDateTime.now());
            if (orderMapper.updateById(order) <= 0) {
                throw new BusinessException("failed to register order refund");
            }
            recordOrderAction(order.getId(), "REFUND_REGISTER", currentStatus.name(), currentStatus.name(),
                    operatorUserId, refundRemark, buildRefundActionExtra(orderActionDTO, refundRecord));
            attachRefundResult(order, refundRecord, false);
            return order;
        }
        Order updated = updateOrderStatus(order, OrderStatus.REFUNDED);
        recordOrderAction(order.getId(), "REFUND_REGISTER", currentStatus.name(), OrderStatus.REFUNDED.name(),
                operatorUserId, refundRemark, buildRefundActionExtra(orderActionDTO, refundRecord));
        attachRefundResult(updated, refundRecord, false);
        return updated;
    }

    @Override
    @Transactional
    public Order verifyVoucher(OrderVoucherVerifyDTO orderVoucherVerifyDTO, Long operatorUserId) {
        return verifyVoucher(orderVoucherVerifyDTO, operatorUserId, null);
    }

    @Override
    @Transactional
    public Order verifyVoucher(OrderVoucherVerifyDTO orderVoucherVerifyDTO, Long operatorUserId, String operatorRoleCode) {
        validateOrderId(orderVoucherVerifyDTO == null ? null : orderVoucherVerifyDTO.getOrderId());
        Order order = dbLockService.lockOrder(orderVoucherVerifyDTO.getOrderId());
        ensureOrderCustomerBound(order);
        OrderStatus currentStatus = getCurrentStatus(order);
        if (!currentStatus.isPaidStage()) {
            throw new BusinessException("only paid orders can be verified");
        }
        String verificationMethod = normalizeVerificationMethod(orderVoucherVerifyDTO.getVerificationMethod());
        if (isDirectDepositVerification(verificationMethod)
                && !systemConfigService.getBoolean(CONFIG_DEPOSIT_DIRECT_ENABLED, true)) {
            throw new BusinessException("direct deposit verification is disabled");
        }
        if (isDirectDepositVerification(verificationMethod)
                && (order.getType() == null || order.getType() != OrderType.DEPOSIT.getCode())) {
            throw new BusinessException("only deposit orders can use direct deposit verification");
        }
        String verificationCode = normalizeVerificationCode(
                orderVoucherVerifyDTO.getVerificationCode(), order.getId(), verificationMethod);
        if (StringUtils.hasText(order.getVerificationStatus()) && "VERIFIED".equalsIgnoreCase(order.getVerificationStatus())) {
            if (!verificationCode.equals(order.getVerificationCode())) {
                throw new BusinessException("order already verified with a different code");
            }
            return order;
        }
        OrderVoucherVerificationResult verificationResult;
        if (isDirectDepositVerification(verificationMethod)) {
            verificationResult = OrderVoucherVerificationResult.skipped();
        } else {
            try {
                verificationResult = voucherVerificationGateway.verify(order, verificationCode, verificationMethod);
            } catch (BusinessException exception) {
                String traceId = buildVoucherFailureTraceId();
                recordVoucherVerificationFailure(order, currentStatus, verificationMethod, verificationCode,
                        operatorUserId, exception, traceId);
                throw new BusinessException(appendTraceId(exception.getMessage(), traceId));
            }
        }

        order.setVerificationStatus("VERIFIED");
        order.setVerificationMethod(verificationMethod);
        order.setVerificationCode(verificationCode);
        order.setVerificationTime(LocalDateTime.now());
        order.setVerificationOperatorId(operatorUserId);
        order.setUpdateTime(LocalDateTime.now());
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException("failed to verify order");
        }
        String actionType = resolveVoucherActionType(verificationMethod, verificationResult);
        recordOrderAction(order.getId(),
                actionType,
                currentStatus.name(), currentStatus.name(),
                operatorUserId, verificationCode,
                buildVoucherVerificationActionExtra(verificationMethod, verificationResult));
        refreshCustomerLifecycle(order.getCustomerId());
        systemFlowRuntimeBridge.recordOrderAction(order, "APPOINTMENT", "ORDER_VERIFY",
                operatorUserId, operatorRoleCode,
                resolveVoucherFlowSummary(verificationMethod, verificationResult));
        return order;
    }

    @Override
    @Transactional
    public Order updateServiceDetail(OrderServiceDetailDTO orderServiceDetailDTO) {
        return updateServiceDetail(orderServiceDetailDTO, null);
    }

    @Override
    @Transactional
    public Order updateServiceDetail(OrderServiceDetailDTO orderServiceDetailDTO, String operatorRoleCode) {
        validateOrderId(orderServiceDetailDTO == null ? null : orderServiceDetailDTO.getOrderId());
        Order order = getOrderById(orderServiceDetailDTO.getOrderId());
        ensureOrderCustomerBound(order);
        if (!"VERIFIED".equalsIgnoreCase(order.getVerificationStatus())) {
            throw new BusinessException("order must be verified before filling service form");
        }
        order.setRemark(StringUtils.hasText(orderServiceDetailDTO.getServiceRequirement())
                ? orderServiceDetailDTO.getServiceRequirement().trim()
                : null);
        order.setServiceDetailJson(normalizeServiceDetailJson(
                orderServiceDetailDTO,
                order.getServiceDetailJson(),
                operatorRoleCode));
        order.setUpdateTime(LocalDateTime.now());
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException("failed to update order service detail");
        }
        refreshCustomerLifecycle(order.getCustomerId());
        return order;
    }

    private String normalizeServiceDetailJson(OrderServiceDetailDTO orderServiceDetailDTO,
                                              String originalServiceDetailJson,
                                              String operatorRoleCode) {
        ObjectNode root = createServiceDetailRoot(orderServiceDetailDTO == null ? null : orderServiceDetailDTO.getServiceDetailJson());
        if (StringUtils.hasText(operatorRoleCode) && !canEditServiceAmounts(operatorRoleCode)) {
            root.put(OrderAmountMaskingSupport.MASK_MARKER, true);
        }
        OrderAmountMaskingSupport.restoreMaskedAmountFields(root, originalServiceDetailJson, objectMapper);
        ObjectNode templateSnapshot = buildServiceTemplateSnapshot(orderServiceDetailDTO);
        if (templateSnapshot != null && !templateSnapshot.isEmpty()) {
            root.set("serviceTemplate", templateSnapshot);
        }
        ServiceFormVersionSupport.reconcileStateAfterSave(root, originalServiceDetailJson, objectMapper);
        if (root.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new BusinessException("服务确认单保存失败，请检查表单内容");
        }
    }

    private boolean canEditServiceAmounts(String operatorRoleCode) {
        if (!StringUtils.hasText(operatorRoleCode)) {
            return true;
        }
        String roleCode = operatorRoleCode.trim().toUpperCase(Locale.ROOT);
        Set<String> roles = parseRoleCodes(systemConfigService.getString(
                CONFIG_SERVICE_AMOUNT_EDIT_ROLES,
                DEFAULT_SERVICE_AMOUNT_EDIT_ROLES));
        if (roles.isEmpty()) {
            roles = parseRoleCodes(DEFAULT_SERVICE_AMOUNT_EDIT_ROLES);
        }
        return roles.contains(roleCode);
    }

    private Set<String> parseRoleCodes(String roleCodes) {
        if (!StringUtils.hasText(roleCodes)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : roleCodes.split("[,，\\s]+")) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim().toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private ObjectNode createServiceDetailRoot(String serviceDetailJson) {
        if (!StringUtils.hasText(serviceDetailJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(serviceDetailJson.trim());
            if (!node.isObject()) {
                throw new BusinessException("服务确认单内容必须是 JSON 对象");
            }
            return (ObjectNode) node;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("服务确认单内容 JSON 格式不正确");
        }
    }

    private ObjectNode buildServiceTemplateSnapshot(OrderServiceDetailDTO orderServiceDetailDTO) {
        if (orderServiceDetailDTO == null || !hasServiceTemplateSnapshot(orderServiceDetailDTO)) {
            return null;
        }
        ObjectNode snapshot = parseTemplateSnapshot(orderServiceDetailDTO.getServiceTemplateSnapshotJson());
        putIfPresent(snapshot, "templateId", orderServiceDetailDTO.getServiceTemplateId());
        putIfPresent(snapshot, "bindingId", orderServiceDetailDTO.getServiceTemplateBindingId());
        putIfPresent(snapshot, "templateCode", orderServiceDetailDTO.getServiceTemplateCode());
        putIfPresent(snapshot, "templateName", orderServiceDetailDTO.getServiceTemplateName());
        putIfPresent(snapshot, "title", orderServiceDetailDTO.getServiceTemplateTitle());
        putIfPresent(snapshot, "layoutMode", orderServiceDetailDTO.getServiceTemplateLayoutMode());
        if (StringUtils.hasText(orderServiceDetailDTO.getServiceTemplateConfigJson())) {
            snapshot.set("config", parseTemplateConfig(orderServiceDetailDTO.getServiceTemplateConfigJson()));
        }
        snapshot.put("snapshotAt", LocalDateTime.now().toString());
        return snapshot;
    }

    private boolean hasServiceTemplateSnapshot(OrderServiceDetailDTO orderServiceDetailDTO) {
        return orderServiceDetailDTO.getServiceTemplateId() != null
                || orderServiceDetailDTO.getServiceTemplateBindingId() != null
                || StringUtils.hasText(orderServiceDetailDTO.getServiceTemplateCode())
                || StringUtils.hasText(orderServiceDetailDTO.getServiceTemplateName())
                || StringUtils.hasText(orderServiceDetailDTO.getServiceTemplateTitle())
                || StringUtils.hasText(orderServiceDetailDTO.getServiceTemplateLayoutMode())
                || StringUtils.hasText(orderServiceDetailDTO.getServiceTemplateConfigJson())
                || StringUtils.hasText(orderServiceDetailDTO.getServiceTemplateSnapshotJson());
    }

    private ObjectNode parseTemplateSnapshot(String snapshotJson) {
        if (!StringUtils.hasText(snapshotJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(snapshotJson.trim());
            if (!node.isObject()) {
                throw new BusinessException("服务单模板快照必须是 JSON 对象");
            }
            return (ObjectNode) node;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("服务单模板快照 JSON 格式不正确");
        }
    }

    private JsonNode parseTemplateConfig(String configJson) {
        try {
            return objectMapper.readTree(configJson.trim());
        } catch (Exception exception) {
            throw new BusinessException("服务单模板配置 JSON 格式不正确");
        }
    }

    private void putIfPresent(ObjectNode node, String fieldName, Long value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }

    private void putIfPresent(ObjectNode node, String fieldName, String value) {
        if (StringUtils.hasText(value)) {
            node.put(fieldName, value.trim());
        }
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

    private List<LocalDateTime> normalizeAppointmentSlots(OrderAppointmentDTO orderAppointmentDTO) {
        if (orderAppointmentDTO == null) {
            throw new BusinessException("appointment request is required");
        }
        LinkedHashSet<LocalDateTime> slots = new LinkedHashSet<>();
        if (orderAppointmentDTO.getAppointmentTime() != null) {
            slots.add(orderAppointmentDTO.getAppointmentTime());
        }
        if (orderAppointmentDTO.getAppointmentSlots() != null) {
            for (LocalDateTime slot : orderAppointmentDTO.getAppointmentSlots()) {
                if (slot != null) {
                    slots.add(slot);
                }
            }
        }
        if (slots.isEmpty()) {
            throw new BusinessException("appointmentTime is required");
        }
        if (slots.size() > 20) {
            throw new BusinessException("appointment slots cannot exceed 20");
        }
        return new ArrayList<>(slots);
    }

    private Integer resolveAppointmentHeadcount(Integer headcount, int slotCount) {
        int normalizedHeadcount = headcount == null ? slotCount : headcount;
        if (normalizedHeadcount <= 0) {
            throw new BusinessException("headcount must be greater than 0");
        }
        if (normalizedHeadcount != slotCount) {
            throw new BusinessException("appointment slots must match headcount");
        }
        return normalizedHeadcount;
    }

    private void assertAppointmentSlotsAvailable(Long orderId, String storeName, List<LocalDateTime> appointmentSlots) {
        if (!StringUtils.hasText(storeName) || appointmentSlots == null || appointmentSlots.isEmpty()) {
            return;
        }
        Long conflictCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .ne(orderId != null, Order::getId, orderId)
                .in(Order::getStatus, ACTIVE_APPOINTMENT_ORDER_STATUSES)
                .eq(Order::getAppointmentStoreName, storeName.trim())
                .in(Order::getAppointmentTime, appointmentSlots));
        if (conflictCount != null && conflictCount > 0) {
            throw new BusinessException("appointment slot already occupied");
        }

        List<Order> activeOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .ne(orderId != null, Order::getId, orderId)
                .in(Order::getStatus, ACTIVE_APPOINTMENT_ORDER_STATUSES)
                .eq(Order::getAppointmentStoreName, storeName.trim()));
        if (activeOrders == null || activeOrders.isEmpty()) {
            return;
        }

        Set<LocalDateTime> requestedSlots = new LinkedHashSet<>(appointmentSlots);
        List<Long> activeOrderIds = activeOrders.stream()
                .map(Order::getId)
                .filter(id -> id != null && id > 0)
                .toList();
        Map<Long, AppointmentActionSnapshot> snapshots = loadLatestAppointmentSnapshots(activeOrderIds);
        for (Order activeOrder : activeOrders) {
            AppointmentActionSnapshot snapshot = snapshots.get(activeOrder.getId());
            List<LocalDateTime> occupiedSlots = snapshot == null || snapshot.slots().isEmpty()
                    ? fallbackAppointmentSlots(activeOrder)
                    : snapshot.slots();
            for (LocalDateTime occupiedSlot : occupiedSlots) {
                if (requestedSlots.contains(occupiedSlot)) {
                    throw new BusinessException("appointment slot already occupied");
                }
            }
        }
    }

    private AppointmentActionSnapshot resolveCurrentAppointmentSnapshot(Order order) {
        if (order == null || order.getId() == null) {
            return new AppointmentActionSnapshot(List.of(), 0);
        }
        AppointmentActionSnapshot snapshot = loadLatestAppointmentSnapshots(List.of(order.getId())).get(order.getId());
        List<LocalDateTime> slots = snapshot == null || snapshot.slots().isEmpty()
                ? fallbackAppointmentSlots(order)
                : snapshot.slots();
        Integer headcount = snapshot == null || snapshot.headcount() == null || snapshot.headcount() <= 0
                ? slots.size()
                : snapshot.headcount();
        return new AppointmentActionSnapshot(slots, headcount);
    }

    private List<LocalDateTime> fallbackAppointmentSlots(Order order) {
        if (order == null || order.getAppointmentTime() == null) {
            return List.of();
        }
        return List.of(order.getAppointmentTime());
    }

    private Map<Long, AppointmentActionSnapshot> loadLatestAppointmentSnapshots(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        List<OrderActionRecord> records = orderActionRecordMapper.selectList(new LambdaQueryWrapper<OrderActionRecord>()
                .in(OrderActionRecord::getOrderId, orderIds)
                .in(OrderActionRecord::getActionType, ACTIVE_APPOINTMENT_ACTIONS)
                .orderByDesc(OrderActionRecord::getCreateTime)
                .orderByDesc(OrderActionRecord::getId));
        Map<Long, AppointmentActionSnapshot> snapshots = new LinkedHashMap<>();
        if (records == null || records.isEmpty()) {
            return snapshots;
        }
        for (OrderActionRecord record : records) {
            if (record.getOrderId() == null || snapshots.containsKey(record.getOrderId())) {
                continue;
            }
            snapshots.put(record.getOrderId(), buildAppointmentSnapshot(record));
        }
        return snapshots;
    }

    private AppointmentActionSnapshot buildAppointmentSnapshot(OrderActionRecord record) {
        if (record == null || !StringUtils.hasText(record.getExtraJson())) {
            return new AppointmentActionSnapshot(List.of(), 0);
        }
        try {
            JsonNode root = objectMapper.readTree(record.getExtraJson());
            List<LocalDateTime> slots = parseAppointmentSlots(root.path("appointmentSlotsAfter"));
            if (slots.isEmpty()) {
                LocalDateTime slot = parseAppointmentDateTime(root.path("appointmentTimeAfter").asText(null));
                slots = slot == null ? List.of() : List.of(slot);
            }
            Integer headcount = positiveInteger(root.path("headcountAfter"));
            if (headcount == null) {
                headcount = positiveInteger(root.path("slotCountAfter"));
            }
            return new AppointmentActionSnapshot(slots, headcount == null ? slots.size() : headcount);
        } catch (Exception exception) {
            return new AppointmentActionSnapshot(List.of(), 0);
        }
    }

    private List<LocalDateTime> parseAppointmentSlots(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<LocalDateTime> slots = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                LocalDateTime parsed = parseAppointmentDateTime(item.asText(null));
                if (parsed != null) {
                    slots.add(parsed);
                }
            }
            return slots;
        }
        LocalDateTime parsed = parseAppointmentDateTime(node.asText(null));
        return parsed == null ? List.of() : List.of(parsed);
    }

    private LocalDateTime parseAppointmentDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace('T', ' ');
        if (normalized.length() == 16) {
            normalized = normalized + ":00";
        }
        if (normalized.length() > 19) {
            normalized = normalized.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(normalized, DATE_TIME_FORMATTER);
        } catch (Exception exception) {
            return null;
        }
    }

    private Integer positiveInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        int value = node.isNumber() ? node.asInt() : Integer.parseInt(node.asText("0"));
        return value > 0 ? value : null;
    }

    private String resolveAppointmentSourceSurface(String sourceSurface) {
        return StringUtils.hasText(sourceSurface) ? sourceSurface.trim() : "CUSTOMER_SCHEDULE";
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

    private String normalizeVerificationCode(String verificationCode, Long orderId, String verificationMethod) {
        if (isDirectDepositVerification(verificationMethod)) {
            return "DIRECT-DEPOSIT-" + orderId;
        }
        if (!StringUtils.hasText(verificationCode)) {
            throw new BusinessException("verification code is required");
        }
        String normalized = verificationCode.trim();
        if (normalized.length() < 4) {
            throw new BusinessException("verification code is invalid");
        }
        return normalized;
    }

    private String normalizeVerificationMethod(String verificationMethod) {
        String normalized = StringUtils.hasText(verificationMethod) ? verificationMethod.trim().toUpperCase() : "MANUAL";
        return switch (normalized) {
            case "SCAN", "SCAN_CAMERA", "CODE", "MANUAL", "EXTERNAL_PROVIDER", "DIRECT_DEPOSIT" -> normalized;
            default -> "MANUAL";
        };
    }

    private boolean isDirectDepositVerification(String verificationMethod) {
        return "DIRECT_DEPOSIT".equalsIgnoreCase(verificationMethod);
    }

    private String resolveVoucherActionType(String verificationMethod, OrderVoucherVerificationResult verificationResult) {
        if (isDirectDepositVerification(verificationMethod)) {
            return "DIRECT_DEPOSIT_VERIFY";
        }
        if (verificationResult != null && verificationResult.externalVerified()) {
            return "EXTERNAL_VOUCHER_VERIFY";
        }
        return "VOUCHER_VERIFY";
    }

    private String resolveVoucherFlowSummary(String verificationMethod, OrderVoucherVerificationResult verificationResult) {
        if (isDirectDepositVerification(verificationMethod)) {
            return "定金免码确认";
        }
        if (verificationResult != null && verificationResult.externalVerified()
                && StringUtils.hasText(verificationResult.providerCode())) {
            return "券码核销：" + verificationResult.providerCode();
        }
        return "券码核销";
    }

    private String buildVoucherVerificationActionExtra(String verificationMethod,
                                                       OrderVoucherVerificationResult verificationResult) {
        if (verificationResult == null) {
            return null;
        }
        return "{"
                + "\"verificationMethod\":" + jsonString(verificationMethod)
                + ",\"providerCode\":" + jsonString(verificationResult.providerCode())
                + ",\"executionMode\":" + jsonString(verificationResult.executionMode())
                + ",\"idempotencyKey\":" + jsonString(verificationResult.idempotencyKey())
                + ",\"externalVerified\":" + verificationResult.externalVerified()
                + ",\"responsePayload\":" + jsonString(verificationResult.responsePayload())
                + "}";
    }

    private void recordVoucherVerificationFailure(Order order,
                                                  OrderStatus currentStatus,
                                                  String verificationMethod,
                                                  String verificationCode,
                                                  Long operatorUserId,
                                                  BusinessException exception,
                                                  String traceId) {
        if (order == null || order.getId() == null) {
            return;
        }
        Runnable recorder = () -> recordOrderAction(order.getId(),
                "VOUCHER_VERIFY_FAILED",
                currentStatus == null ? null : currentStatus.name(),
                currentStatus == null ? null : currentStatus.name(),
                operatorUserId,
                "券码核销失败：" + safeBusinessMessage(exception.getMessage()),
                buildVoucherVerificationFailureExtra(order, verificationMethod, verificationCode, exception, traceId));
        try {
            if (voucherAuditTransactionTemplate == null) {
                recorder.run();
            } else {
                voucherAuditTransactionTemplate.executeWithoutResult(status -> recorder.run());
            }
        } catch (Exception auditException) {
            log.warn("failed to record voucher verification failure, orderId={}, traceId={}",
                    order.getId(), traceId, auditException);
        }
    }

    private String buildVoucherVerificationFailureExtra(Order order,
                                                        String verificationMethod,
                                                        String verificationCode,
                                                        BusinessException exception,
                                                        String traceId) {
        String providerCode = resolveVoucherProviderCode(order);
        return "{"
                + "\"traceId\":" + jsonString(traceId)
                + ",\"verificationMethod\":" + jsonString(verificationMethod)
                + ",\"providerCode\":" + jsonString(providerCode)
                + ",\"executionMode\":\"FAILED_BEFORE_LOCAL_UPDATE\""
                + ",\"idempotencyKey\":" + jsonString(buildVoucherFailureIdempotencyKey(providerCode, order, verificationCode))
                + ",\"externalVerified\":false"
                + ",\"failureReason\":" + jsonString(safeBusinessMessage(exception.getMessage()))
                + "}";
    }

    private String resolveVoucherProviderCode(Order order) {
        if (order == null) {
            return "UNKNOWN";
        }
        if (StringUtils.hasText(order.getExternalPartnerCode())) {
            return order.getExternalPartnerCode().trim().toUpperCase(Locale.ROOT);
        }
        String sourceChannel = SourceChannel.resolveCode(order.getSourceChannel(), order.getSource());
        if (SourceChannel.DISTRIBUTOR.name().equals(sourceChannel)) {
            return "DISTRIBUTION";
        }
        if (SourceChannel.DOUYIN.name().equals(sourceChannel)) {
            return "DOUYIN_LAIKE";
        }
        return StringUtils.hasText(sourceChannel) ? sourceChannel : "UNKNOWN";
    }

    private String buildVoucherFailureIdempotencyKey(String providerCode, Order order, String verificationCode) {
        return "VOUCHER_VERIFY:" + firstNonBlank(providerCode, "UNKNOWN") + ":" + order.getId() + ":" + verificationCode;
    }

    private String buildVoucherFailureTraceId() {
        return "VFY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String appendTraceId(String message, String traceId) {
        String cleanMessage = safeBusinessMessage(message);
        if (!StringUtils.hasText(traceId) || cleanMessage.contains(traceId)) {
            return cleanMessage;
        }
        return cleanMessage + "（追踪编号：" + traceId + "）";
    }

    private String safeBusinessMessage(String message) {
        String clean = StringUtils.hasText(message) ? message.trim() : "未知错误";
        clean = clean.replaceAll("(?i)(access_token|refresh_token|client_secret|auth_code|token)=([^\\s&]+)", "$1=****");
        return clean.length() > 240 ? clean.substring(0, 240) : clean;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
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

    private String buildRefundActionExtra(OrderActionDTO orderActionDTO) {
        return buildRefundActionExtra(orderActionDTO, null);
    }

    private String buildRefundActionExtra(OrderActionDTO orderActionDTO, OrderRefundRecord refundRecord) {
        if (orderActionDTO == null) {
            return null;
        }
        String scene = resolveRefundScene(orderActionDTO);
        String scope = REFUND_SCENE_FINANCE_PAYMENT.equals(scene) ? "VERIFIED_PAYMENT" : "STORE_SERVICE_CONTENT";
        boolean reverseStorePerformance = REFUND_SCENE_STORE_SERVICE.equals(scene);
        boolean reverseCustomerService = REFUND_SCENE_FINANCE_PAYMENT.equals(scene)
                && Boolean.TRUE.equals(orderActionDTO.getReverseCustomerService());
        boolean reverseDistributor = REFUND_SCENE_FINANCE_PAYMENT.equals(scene)
                && Boolean.TRUE.equals(orderActionDTO.getReverseDistributor());
        return "{"
                + "\"refundScene\":" + jsonString(scene)
                + ",\"refundRecordId\":" + (refundRecord == null || refundRecord.getId() == null ? "null" : refundRecord.getId())
                + ",\"refundIdempotencyKey\":" + jsonString(refundRecord == null ? null : refundRecord.getIdempotencyKey())
                + ",\"platformChannel\":" + jsonString(orderActionDTO.getPlatformChannel())
                + ",\"reverseStorePerformance\":" + reverseStorePerformance
                + ",\"reverseSalary\":" + reverseStorePerformance
                + ",\"reverseCustomerService\":" + reverseCustomerService
                + ",\"reverseDistributor\":" + reverseDistributor
                + ",\"fundsTransferred\":false"
                + ",\"scope\":" + jsonString(scope)
                + "}";
    }

    private String buildAppointmentActionExtra(LocalDateTime previousAppointmentTime,
                                               LocalDateTime nextAppointmentTime,
                                               List<LocalDateTime> previousAppointmentSlots,
                                               List<LocalDateTime> nextAppointmentSlots,
                                               Integer previousHeadcount,
                                               Integer nextHeadcount,
                                               String previousStoreName,
                                               String storeName,
                                               String remark,
                                               String reasonType,
                                               String operatorRoleCode,
                                               String sourceSurface) {
        return "{"
                + "\"appointmentTimeBefore\":" + jsonString(formatDateTime(previousAppointmentTime))
                + ",\"appointmentTimeAfter\":" + jsonString(formatDateTime(nextAppointmentTime))
                + ",\"appointmentSlotsBefore\":" + jsonStringArray(formatDateTimes(previousAppointmentSlots))
                + ",\"appointmentSlotsAfter\":" + jsonStringArray(formatDateTimes(nextAppointmentSlots))
                + ",\"headcountBefore\":" + jsonNumber(previousHeadcount)
                + ",\"headcountAfter\":" + jsonNumber(nextHeadcount)
                + ",\"slotCountBefore\":" + jsonNumber(previousAppointmentSlots == null ? 0 : previousAppointmentSlots.size())
                + ",\"slotCountAfter\":" + jsonNumber(nextAppointmentSlots == null ? 0 : nextAppointmentSlots.size())
                + ",\"storeNameBefore\":" + jsonString(previousStoreName)
                + ",\"storeNameAfter\":" + jsonString(storeName)
                + ",\"reasonType\":" + jsonString(reasonType)
                + ",\"operatorRoleCode\":" + jsonString(operatorRoleCode)
                + ",\"sourceSurface\":" + jsonString(sourceSurface)
                + ",\"remark\":" + jsonString(remark)
                + "}";
    }

    private String resolveAppointmentReasonType(String value, String defaultValue) {
        String raw = StringUtils.hasText(value) ? value.trim() : defaultValue;
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(ACTION_TIME_FORMATTER);
    }

    private List<String> formatDateTimes(List<LocalDateTime> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> value == null ? null : value.format(DATE_TIME_FORMATTER))
                .toList();
    }

    private void validateRefundRequest(Order order, OrderActionDTO orderActionDTO) {
        if (orderActionDTO == null) {
            throw new BusinessException("refund request is required");
        }
        String scene = resolveRefundScene(orderActionDTO);
        if (REFUND_SCENE_STORE_SERVICE.equals(scene)) {
            BigDecimal serviceRefundAmount = orderActionDTO.getServiceRefundAmount();
            if (serviceRefundAmount == null || serviceRefundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("门店退款金额必须大于 0");
            }
            if (getCurrentStatus(order) != OrderStatus.COMPLETED) {
                throw new BusinessException("门店服务内容退款仅支持已完成订单");
            }
            ensureFinishedPlanOrder(order.getId());
            BigDecimal serviceConfirmAmount = resolveServiceConfirmAmount(order);
            if (serviceConfirmAmount == null || serviceConfirmAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("服务确认单金额缺失，需先补录确认单后再登记退款");
            }
            BigDecimal remainingAmount = serviceConfirmAmount.subtract(sumRegisteredRefundAmount(order.getId(), scene));
            if (serviceRefundAmount.compareTo(remainingAmount) > 0) {
                throw new BusinessException("门店退款金额不能超过剩余可退确认单金额：" + scaleMoney(remainingAmount));
            }
            if (Boolean.TRUE.equals(orderActionDTO.getReverseDistributor())
                    || Boolean.TRUE.equals(orderActionDTO.getReverseCustomerService())) {
                throw new BusinessException("门店服务退款只能冲正门店人员绩效");
            }
            if (!StringUtils.hasText(orderActionDTO.getRefundReason())
                    && !StringUtils.hasText(orderActionDTO.getRemark())) {
                throw new BusinessException("退款原因不能为空");
            }
            return;
        }
        BigDecimal refundAmount = orderActionDTO.getRefundAmount();
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("已核销金额退款必须填写退款金额");
        }
        if (!"VERIFIED".equalsIgnoreCase(order.getVerificationStatus())) {
            throw new BusinessException("仅已核销订单可登记团购/定金退款");
        }
        BigDecimal remainingAmount = resolveVerifiedPaymentAmount(order).subtract(sumRegisteredRefundAmount(order.getId(), scene));
        if (refundAmount.compareTo(remainingAmount) > 0) {
            throw new BusinessException("团购/定金退款金额不能超过剩余可退核销金额：" + scaleMoney(remainingAmount));
        }
        if (Boolean.TRUE.equals(orderActionDTO.getReverseStorePerformance())) {
            throw new BusinessException("财务退款只冲正客服与分销绩效，不冲正门店绩效");
        }
        if (!StringUtils.hasText(orderActionDTO.getRefundReason())
                && !StringUtils.hasText(orderActionDTO.getRemark())) {
            throw new BusinessException("退款原因不能为空");
        }
    }

    private OrderRefundRecord findSameRefund(Order order, OrderActionDTO orderActionDTO) {
        String idempotencyKey = buildRefundIdempotencyKey(order, orderActionDTO);
        OrderRefundRecord existing = orderRefundRecordMapper.selectOne(new LambdaQueryWrapper<OrderRefundRecord>()
                .eq(OrderRefundRecord::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
        if (existing != null) {
            assertSameRefundRequest(existing, order, orderActionDTO);
        }
        return existing;
    }

    private RefundRegistration createRefundRecord(Order order, OrderActionDTO orderActionDTO, Long operatorUserId) {
        String scene = resolveRefundScene(orderActionDTO);
        LocalDateTime now = LocalDateTime.now();
        PlanOrder planOrder = findPlanOrder(order.getId());
        OrderRefundRecord record = new OrderRefundRecord();
        record.setOrderId(order.getId());
        record.setPlanOrderId(planOrder == null ? null : planOrder.getId());
        record.setRefundScene(scene);
        record.setRefundObject(REFUND_SCENE_FINANCE_PAYMENT.equals(scene)
                ? "VERIFIED_PAYMENT"
                : "STORE_SERVICE_CONTENT");
        record.setRefundAmount(scaleMoney(resolveRefundAmount(orderActionDTO)));
        record.setRefundReasonType(trimToNull(orderActionDTO.getRefundReasonType()));
        record.setRefundReason(trimToNull(firstText(orderActionDTO.getRefundReason(), orderActionDTO.getRemark())));
        record.setStatus("REGISTERED");
        record.setIdempotencyKey(buildRefundIdempotencyKey(order, orderActionDTO));
        record.setOutOrderNo(trimToNull(firstText(orderActionDTO.getOutOrderNo(), order.getOrderNo())));
        record.setOutRefundNo(trimToNull(firstText(orderActionDTO.getOutRefundNo(), buildDefaultOutRefundNo(order, scene))));
        record.setExternalRefundId(trimToNull(orderActionDTO.getExternalRefundId()));
        record.setItemOrderId(trimToNull(orderActionDTO.getItemOrderId()));
        record.setNotifyUrl(trimToNull(orderActionDTO.getNotifyUrl()));
        record.setPlatformChannel(trimToNull(firstText(orderActionDTO.getPlatformChannel(), order.getSourceChannel())));
        record.setOperatorUserId(operatorUserId);
        record.setReverseStorePerformance(REFUND_SCENE_STORE_SERVICE.equals(scene) ? 1 : 0);
        record.setReverseCustomerService(REFUND_SCENE_FINANCE_PAYMENT.equals(scene)
                && Boolean.TRUE.equals(orderActionDTO.getReverseCustomerService()) ? 1 : 0);
        record.setReverseDistributor(REFUND_SCENE_FINANCE_PAYMENT.equals(scene)
                && Boolean.TRUE.equals(orderActionDTO.getReverseDistributor()) ? 1 : 0);
        record.setRawRequest(buildRefundActionExtra(orderActionDTO));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            if (orderRefundRecordMapper.insert(record) <= 0) {
                throw new BusinessException("refund ledger registration failed");
            }
        } catch (DuplicateKeyException exception) {
            OrderRefundRecord existing = findSameRefund(order, orderActionDTO);
            if (existing == null) {
                throw new BusinessException("refund request already registered, please retry query");
            }
            return new RefundRegistration(existing, true);
        }
        return new RefundRegistration(record, false);
    }

    private void assertSameRefundRequest(OrderRefundRecord existing, Order order, OrderActionDTO orderActionDTO) {
        if (existing == null) {
            return;
        }
        if (order == null || !existing.getOrderId().equals(order.getId())) {
            throw new BusinessException("refund idempotency key has already been used by another order");
        }
        String expectedScene = resolveRefundScene(orderActionDTO);
        if (!normalize(existing.getRefundScene()).equals(expectedScene)) {
            throw new BusinessException("refund idempotency key conflicts with another refund scene");
        }
        BigDecimal existingAmount = scaleMoney(existing.getRefundAmount());
        BigDecimal expectedAmount = scaleMoney(resolveRefundAmount(orderActionDTO));
        if (existingAmount.compareTo(expectedAmount) != 0) {
            throw new BusinessException("refund idempotency key conflicts with another refund amount");
        }
        assertSameRefundText(existing.getRefundReasonType(), orderActionDTO.getRefundReasonType(), "reason type");
        assertSameRefundText(existing.getRefundReason(),
                firstText(orderActionDTO.getRefundReason(), orderActionDTO.getRemark()), "reason");
        assertSameRefundFlag(existing.getReverseStorePerformance(), expectedReverseStorePerformance(expectedScene),
                "store performance reversal");
        assertSameRefundFlag(existing.getReverseCustomerService(), expectedReverseCustomerService(expectedScene, orderActionDTO),
                "customer service reversal");
        assertSameRefundFlag(existing.getReverseDistributor(), expectedReverseDistributor(expectedScene, orderActionDTO),
                "distributor reversal");
    }

    private void assertSameRefundText(String existing, String expected, String label) {
        if (!normalize(existing).equals(normalize(expected))) {
            throw new BusinessException("refund idempotency key conflicts with another refund " + label);
        }
    }

    private void assertSameRefundFlag(Integer existing, int expected, String label) {
        if ((existing == null ? 0 : existing) != expected) {
            throw new BusinessException("refund idempotency key conflicts with another " + label);
        }
    }

    private int expectedReverseStorePerformance(String scene) {
        return REFUND_SCENE_STORE_SERVICE.equals(scene) ? 1 : 0;
    }

    private int expectedReverseCustomerService(String scene, OrderActionDTO orderActionDTO) {
        return REFUND_SCENE_FINANCE_PAYMENT.equals(scene)
                && orderActionDTO != null
                && Boolean.TRUE.equals(orderActionDTO.getReverseCustomerService()) ? 1 : 0;
    }

    private int expectedReverseDistributor(String scene, OrderActionDTO orderActionDTO) {
        return REFUND_SCENE_FINANCE_PAYMENT.equals(scene)
                && orderActionDTO != null
                && Boolean.TRUE.equals(orderActionDTO.getReverseDistributor()) ? 1 : 0;
    }

    private void attachRefundResult(Order order, OrderRefundRecord refundRecord, boolean duplicate) {
        if (order == null || refundRecord == null) {
            return;
        }
        order.setRefundRecordId(refundRecord.getId());
        order.setRefundIdempotencyKey(refundRecord.getIdempotencyKey());
        order.setRefundDuplicate(duplicate);
    }

    private record RefundRegistration(OrderRefundRecord record, boolean duplicate) {
    }

    private void createSalaryReversalDetails(Order order, OrderActionDTO orderActionDTO, OrderRefundRecord refundRecord) {
        PlanOrder planOrder = findPlanOrder(order.getId());
        if (planOrder == null || refundRecord == null || refundRecord.getId() == null) {
            return;
        }
        List<SalaryDetail> details = salaryDetailMapper.selectList(new LambdaQueryWrapper<SalaryDetail>()
                .eq(SalaryDetail::getPlanOrderId, planOrder.getId())
                .isNull(SalaryDetail::getAdjustmentType)
                .gt(SalaryDetail::getAmount, BigDecimal.ZERO)
                .orderByAsc(SalaryDetail::getCreateTime, SalaryDetail::getId));
        if (details.isEmpty()) {
            return;
        }
        String scene = resolveRefundScene(orderActionDTO);
        BigDecimal orderAmount = order.getAmount();
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal refundAmount = scaleMoney(resolveRefundAmount(orderActionDTO));
        LocalDateTime now = LocalDateTime.now();
        for (SalaryDetail detail : details) {
            if (!shouldReverseSalaryDetail(scene, detail.getRoleCode(), orderActionDTO)) {
                continue;
            }
            BigDecimal adjustmentAmount = scaleMoney(detail.getAmount()
                    .multiply(refundAmount)
                    .divide(orderAmount, 8, RoundingMode.HALF_UP))
                    .negate();
            if (adjustmentAmount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            SalaryDetail adjustment = new SalaryDetail();
            adjustment.setPlanOrderId(planOrder.getId());
            adjustment.setUserId(detail.getUserId());
            adjustment.setRoleCode(detail.getRoleCode());
            adjustment.setOrderAmount(refundAmount.negate());
            adjustment.setAmount(adjustmentAmount);
            adjustment.setAdjustmentType("REFUND_REVERSAL");
            adjustment.setRefundRecordId(refundRecord.getId());
            adjustment.setSourceSalaryDetailId(detail.getId());
            adjustment.setCreateTime(now);
            salaryDetailMapper.insert(adjustment);
        }
    }

    private boolean shouldReverseSalaryDetail(String scene, String roleCode, OrderActionDTO orderActionDTO) {
        String normalizedRole = normalize(roleCode).toUpperCase(Locale.ROOT);
        if (REFUND_SCENE_STORE_SERVICE.equals(scene)) {
            return STORE_PERFORMANCE_ROLES.contains(normalizedRole);
        }
        boolean customerServiceRole = FINANCE_CUSTOMER_SERVICE_REVERSE_ROLES.contains(normalizedRole)
                || normalizedRole.contains("CUSTOMER_SERVICE");
        boolean distributorRole = normalizedRole.contains("DISTRIBUT");
        return (customerServiceRole && Boolean.TRUE.equals(orderActionDTO.getReverseCustomerService()))
                || (distributorRole && Boolean.TRUE.equals(orderActionDTO.getReverseDistributor()));
    }

    private BigDecimal sumRegisteredRefundAmount(Long orderId, String scene) {
        return orderRefundRecordMapper.selectList(new LambdaQueryWrapper<OrderRefundRecord>()
                        .eq(OrderRefundRecord::getOrderId, orderId)
                        .eq(OrderRefundRecord::getRefundScene, scene))
                .stream()
                .filter(record -> {
                    String status = normalize(record.getStatus()).toUpperCase(Locale.ROOT);
                    return !"FAILED".equals(status) && !"CANCELLED".equals(status) && !"REJECTED".equals(status);
                })
                .map(OrderRefundRecord::getRefundAmount)
                .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal resolveServiceConfirmAmount(Order order) {
        if (!StringUtils.hasText(order.getServiceDetailJson())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(order.getServiceDetailJson());
            JsonNode amountNode = root.path("serviceConfirmAmount");
            if (amountNode.isMissingNode() || amountNode.isNull()) {
                return null;
            }
            BigDecimal amount = amountNode.isNumber()
                    ? amountNode.decimalValue()
                    : new BigDecimal(amountNode.asText().trim());
            return amount.compareTo(BigDecimal.ZERO) > 0 ? scaleMoney(amount) : null;
        } catch (Exception exception) {
            throw new BusinessException("服务确认单金额解析失败，请先修正确认单");
        }
    }

    private BigDecimal resolveVerifiedPaymentAmount(Order order) {
        BigDecimal deposit = order.getDeposit();
        if (deposit != null && deposit.compareTo(BigDecimal.ZERO) > 0) {
            return scaleMoney(deposit);
        }
        return scaleMoney(order.getAmount() == null ? BigDecimal.ZERO : order.getAmount());
    }

    private void ensureFinishedPlanOrder(Long orderId) {
        PlanOrder planOrder = findPlanOrder(orderId);
        if (planOrder == null) {
            throw new BusinessException("服务确认单记录不存在，需先补录确认单后再登记退款");
        }
        if (!PlanOrderStatus.FINISHED.name().equals(planOrder.getStatus())) {
            throw new BusinessException("门店服务未完成，暂不能登记门店服务内容退款");
        }
    }

    private PlanOrder findPlanOrder(Long orderId) {
        return planOrderMapper.selectOne(new LambdaQueryWrapper<PlanOrder>()
                .eq(PlanOrder::getOrderId, orderId)
                .last("LIMIT 1"));
    }

    private String buildRefundIdempotencyKey(Order order, OrderActionDTO orderActionDTO) {
        String rawKey;
        if (StringUtils.hasText(orderActionDTO.getIdempotencyKey())) {
            rawKey = orderActionDTO.getIdempotencyKey().trim();
        } else {
            rawKey = "ORDER_REFUND:" + order.getId()
                    + ":" + resolveRefundScene(orderActionDTO)
                    + ":" + jsonNumber(resolveRefundAmount(orderActionDTO))
                    + ":" + normalize(firstText(orderActionDTO.getRefundReasonType(), "NONE"))
                    + ":" + normalize(firstText(orderActionDTO.getRefundReason(), orderActionDTO.getRemark(), "NONE"));
        }
        return "ORDER_REFUND:" + sha256Hex(rawKey);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException("退款幂等键生成失败");
        }
    }

    private String buildDefaultOutRefundNo(Order order, String scene) {
        return "RF" + order.getId() + scene.substring(0, Math.min(3, scene.length())) + System.currentTimeMillis();
    }

    private String resolveRefundScene(OrderActionDTO orderActionDTO) {
        String scene = orderActionDTO == null ? null : orderActionDTO.getRefundScene();
        String normalizedScene = StringUtils.hasText(scene)
                ? scene.trim().toUpperCase(Locale.ROOT)
                : REFUND_SCENE_STORE_SERVICE;
        return REFUND_SCENE_FINANCE_PAYMENT.equals(normalizedScene)
                ? REFUND_SCENE_FINANCE_PAYMENT
                : REFUND_SCENE_STORE_SERVICE;
    }

    private BigDecimal resolveRefundAmount(OrderActionDTO orderActionDTO) {
        if (orderActionDTO == null) {
            return null;
        }
        if (REFUND_SCENE_FINANCE_PAYMENT.equals(resolveRefundScene(orderActionDTO))) {
            return orderActionDTO.getRefundAmount();
        }
        return orderActionDTO.getServiceRefundAmount();
    }

    private String buildRefundRemark(OrderActionDTO orderActionDTO) {
        if (orderActionDTO == null) {
            return null;
        }
        if (StringUtils.hasText(orderActionDTO.getRemark())) {
            return orderActionDTO.getRemark().trim();
        }
        String reason = firstText(orderActionDTO.getRefundReason(), orderActionDTO.getRefundReasonType(), "退款登记");
        return REFUND_SCENE_FINANCE_PAYMENT.equals(resolveRefundScene(orderActionDTO))
                ? "财务登记已核销金额退款：" + reason
                : "门店服务内容退款：" + reason;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String jsonNumber(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    private String jsonNumber(Integer value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private String jsonStringArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index += 1) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(jsonString(values.get(index)));
        }
        return builder.append(']').toString();
    }

    private String jsonString(String value) {
        if (!StringUtils.hasText(value)) {
            return "null";
        }
        return "\"" + value.trim()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }
}
