package com.seedcrm.crm.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.seedcrm.crm.order.util.OrderNoGenerator;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final String REFUND_SCENE_STORE_SERVICE = "STORE_SERVICE";
    private static final String REFUND_SCENE_FINANCE_PAYMENT = "FINANCE_VERIFIED_PAYMENT";
    private static final Set<String> STORE_PERFORMANCE_ROLES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");
    private static final Set<String> FINANCE_REVERSE_ROLES = Set.of(
            "ONLINE_CUSTOMER_SERVICE",
            "CLUE_MANAGER",
            "CONSULTANT",
            "DISTRIBUTOR",
            "DISTRIBUTION");

    private final OrderMapper orderMapper;
    private final ClueMapper clueMapper;
    private final CustomerService customerService;
    private final CustomerTagService customerTagService;
    private final PlanOrderMapper planOrderMapper;
    private final DistributorIncomeService distributorIncomeService;
    private final DbLockService dbLockService;
    private final OrderSettlementService orderSettlementService;
    private final OrderActionRecordMapper orderActionRecordMapper;
    private final OrderRefundRecordMapper orderRefundRecordMapper;
    private final SalaryDetailMapper salaryDetailMapper;
    private final ObjectMapper objectMapper;

    public OrderServiceImpl(OrderMapper orderMapper,
                            ClueMapper clueMapper,
                            CustomerService customerService,
                            CustomerTagService customerTagService,
                            PlanOrderMapper planOrderMapper,
                            DistributorIncomeService distributorIncomeService,
                            DbLockService dbLockService,
                            OrderSettlementService orderSettlementService,
                            OrderActionRecordMapper orderActionRecordMapper,
                            OrderRefundRecordMapper orderRefundRecordMapper,
                            SalaryDetailMapper salaryDetailMapper,
                            ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.clueMapper = clueMapper;
        this.customerService = customerService;
        this.customerTagService = customerTagService;
        this.planOrderMapper = planOrderMapper;
        this.distributorIncomeService = distributorIncomeService;
        this.dbLockService = dbLockService;
        this.orderSettlementService = orderSettlementService;
        this.orderActionRecordMapper = orderActionRecordMapper;
        this.orderRefundRecordMapper = orderRefundRecordMapper;
        this.salaryDetailMapper = salaryDetailMapper;
        this.objectMapper = objectMapper;
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
        OrderStatus currentStatus = getCurrentStatus(order);
        if (currentStatus != OrderStatus.APPOINTMENT) {
            assertNextStatus(order, OrderStatus.APPOINTMENT);
        }
        order.setAppointmentTime(orderAppointmentDTO.getAppointmentTime());
        updateRemark(order, orderAppointmentDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.APPOINTMENT);
    }

    @Override
    @Transactional
    public Order cancelAppointment(OrderActionDTO orderActionDTO) {
        validateOrderId(orderActionDTO == null ? null : orderActionDTO.getOrderId());
        Order order = getOrderById(orderActionDTO.getOrderId());
        ensureOrderCustomerBound(order);
        if (getCurrentStatus(order) != OrderStatus.APPOINTMENT) {
            throw new BusinessException("only appointment order can cancel appointment");
        }
        order.setAppointmentTime(null);
        updateRemark(order, orderActionDTO.getRemark());
        return updateOrderStatus(order, OrderStatus.PAID_DEPOSIT);
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
        OrderStatus currentStatus = getCurrentStatus(order);
        if (!currentStatus.canRefund()) {
            throw new BusinessException("order cannot be refunded from status " + currentStatus.name());
        }
        validateRefundRequest(order, orderActionDTO);
        OrderRefundRecord existingRefund = findSameRefund(order, orderActionDTO);
        if (existingRefund != null) {
            return order;
        }
        String refundRemark = buildRefundRemark(orderActionDTO);
        OrderRefundRecord refundRecord = createRefundRecord(order, orderActionDTO, operatorUserId);
        createSalaryReversalDetails(order, orderActionDTO, refundRecord);
        updateRemark(order, refundRemark);
        if (currentStatus == OrderStatus.COMPLETED) {
            order.setUpdateTime(LocalDateTime.now());
            if (orderMapper.updateById(order) <= 0) {
                throw new BusinessException("failed to register order refund");
            }
            recordOrderAction(order.getId(), "REFUND_REGISTER", currentStatus.name(), currentStatus.name(),
                    operatorUserId, refundRemark, buildRefundActionExtra(orderActionDTO));
            return order;
        }
        Order updated = updateOrderStatus(order, OrderStatus.REFUNDED);
        recordOrderAction(order.getId(), "REFUND_REGISTER", currentStatus.name(), OrderStatus.REFUNDED.name(),
                operatorUserId, refundRemark, buildRefundActionExtra(orderActionDTO));
        return updated;
    }

    @Override
    @Transactional
    public Order verifyVoucher(OrderVoucherVerifyDTO orderVoucherVerifyDTO, Long operatorUserId) {
        validateOrderId(orderVoucherVerifyDTO == null ? null : orderVoucherVerifyDTO.getOrderId());
        Order order = dbLockService.lockOrder(orderVoucherVerifyDTO.getOrderId());
        ensureOrderCustomerBound(order);
        OrderStatus currentStatus = getCurrentStatus(order);
        if (!currentStatus.isPaidStage()) {
            throw new BusinessException("only paid orders can be verified");
        }
        String verificationCode = normalizeVerificationCode(orderVoucherVerifyDTO.getVerificationCode(), order.getId());
        if (StringUtils.hasText(order.getVerificationStatus()) && "VERIFIED".equalsIgnoreCase(order.getVerificationStatus())) {
            if (!verificationCode.equals(order.getVerificationCode())) {
                throw new BusinessException("order already verified with a different code");
            }
            return order;
        }

        order.setVerificationStatus("VERIFIED");
        order.setVerificationMethod(normalizeVerificationMethod(orderVoucherVerifyDTO.getVerificationMethod()));
        order.setVerificationCode(verificationCode);
        order.setVerificationTime(LocalDateTime.now());
        order.setVerificationOperatorId(operatorUserId);
        order.setUpdateTime(LocalDateTime.now());
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException("failed to verify order");
        }
        recordOrderAction(order.getId(), "VOUCHER_VERIFY", currentStatus.name(), currentStatus.name(),
                operatorUserId, verificationCode);
        refreshCustomerLifecycle(order.getCustomerId());
        return order;
    }

    @Override
    @Transactional
    public Order updateServiceDetail(OrderServiceDetailDTO orderServiceDetailDTO) {
        validateOrderId(orderServiceDetailDTO == null ? null : orderServiceDetailDTO.getOrderId());
        Order order = getOrderById(orderServiceDetailDTO.getOrderId());
        ensureOrderCustomerBound(order);
        if (!"VERIFIED".equalsIgnoreCase(order.getVerificationStatus())) {
            throw new BusinessException("order must be verified before filling service form");
        }
        order.setRemark(StringUtils.hasText(orderServiceDetailDTO.getServiceRequirement())
                ? orderServiceDetailDTO.getServiceRequirement().trim()
                : null);
        order.setServiceDetailJson(StringUtils.hasText(orderServiceDetailDTO.getServiceDetailJson())
                ? orderServiceDetailDTO.getServiceDetailJson().trim()
                : null);
        order.setUpdateTime(LocalDateTime.now());
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException("failed to update order service detail");
        }
        refreshCustomerLifecycle(order.getCustomerId());
        return order;
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

    private String normalizeVerificationCode(String verificationCode, Long orderId) {
        String normalized = StringUtils.hasText(verificationCode)
                ? verificationCode.trim()
                : "MOCK-" + orderId;
        if (normalized.length() < 4) {
            throw new BusinessException("verification code is invalid");
        }
        return normalized;
    }

    private String normalizeVerificationMethod(String verificationMethod) {
        String normalized = StringUtils.hasText(verificationMethod) ? verificationMethod.trim().toUpperCase() : "MANUAL";
        return switch (normalized) {
            case "SCAN", "SCAN_CAMERA", "CODE", "MANUAL", "EXTERNAL_PROVIDER" -> normalized;
            default -> "MANUAL";
        };
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
        if (orderActionDTO == null) {
            return null;
        }
        String scene = resolveRefundScene(orderActionDTO);
        String scope = REFUND_SCENE_FINANCE_PAYMENT.equals(scene) ? "VERIFIED_PAYMENT" : "STORE_SERVICE_CONTENT";
        boolean reverseStorePerformance = REFUND_SCENE_STORE_SERVICE.equals(scene)
                && (Boolean.TRUE.equals(orderActionDTO.getReverseStorePerformance())
                || Boolean.TRUE.equals(orderActionDTO.getReverseSalary()));
        boolean reverseCustomerService = REFUND_SCENE_FINANCE_PAYMENT.equals(scene)
                && Boolean.TRUE.equals(orderActionDTO.getReverseCustomerService());
        boolean reverseDistributor = REFUND_SCENE_FINANCE_PAYMENT.equals(scene)
                && Boolean.TRUE.equals(orderActionDTO.getReverseDistributor());
        return "{"
                + "\"refundScene\":" + jsonString(scene)
                + ",\"refundAmount\":" + jsonNumber(resolveRefundAmount(orderActionDTO))
                + ",\"serviceRefundAmount\":" + jsonNumber(orderActionDTO.getServiceRefundAmount())
                + ",\"refundReasonType\":" + jsonString(orderActionDTO.getRefundReasonType())
                + ",\"refundReason\":" + jsonString(orderActionDTO.getRefundReason())
                + ",\"outOrderNo\":" + jsonString(orderActionDTO.getOutOrderNo())
                + ",\"outRefundNo\":" + jsonString(orderActionDTO.getOutRefundNo())
                + ",\"externalRefundId\":" + jsonString(orderActionDTO.getExternalRefundId())
                + ",\"itemOrderId\":" + jsonString(orderActionDTO.getItemOrderId())
                + ",\"notifyUrl\":" + jsonString(orderActionDTO.getNotifyUrl())
                + ",\"platformChannel\":" + jsonString(orderActionDTO.getPlatformChannel())
                + ",\"reverseStorePerformance\":" + reverseStorePerformance
                + ",\"reverseSalary\":" + reverseStorePerformance
                + ",\"reverseCustomerService\":" + reverseCustomerService
                + ",\"reverseDistributor\":" + reverseDistributor
                + ",\"fundsTransferred\":false"
                + ",\"scope\":" + jsonString(scope)
                + "}";
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
        return orderRefundRecordMapper.selectOne(new LambdaQueryWrapper<OrderRefundRecord>()
                .eq(OrderRefundRecord::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    private OrderRefundRecord createRefundRecord(Order order, OrderActionDTO orderActionDTO, Long operatorUserId) {
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
        if (orderRefundRecordMapper.insert(record) <= 0) {
            throw new BusinessException("退款流水登记失败");
        }
        return record;
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
            if (!shouldReverseSalaryDetail(scene, detail.getRoleCode())) {
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

    private boolean shouldReverseSalaryDetail(String scene, String roleCode) {
        String normalizedRole = normalize(roleCode).toUpperCase(Locale.ROOT);
        if (REFUND_SCENE_STORE_SERVICE.equals(scene)) {
            return STORE_PERFORMANCE_ROLES.contains(normalizedRole);
        }
        return FINANCE_REVERSE_ROLES.contains(normalizedRole)
                || normalizedRole.contains("CUSTOMER_SERVICE")
                || normalizedRole.contains("DISTRIBUT");
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
