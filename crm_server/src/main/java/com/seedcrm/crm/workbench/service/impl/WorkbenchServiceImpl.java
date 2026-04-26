package com.seedcrm.crm.workbench.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.entity.CustomerEcomUser;
import com.seedcrm.crm.customer.entity.CustomerTagDetail;
import com.seedcrm.crm.customer.mapper.CustomerEcomUserMapper;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.customer.mapper.CustomerTagDetailMapper;
import com.seedcrm.crm.distributor.dto.DistributorStatsResponse;
import com.seedcrm.crm.distributor.entity.Distributor;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorMapper;
import com.seedcrm.crm.distributor.mapper.DistributorWithdrawMapper;
import com.seedcrm.crm.distributor.service.DistributorService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import com.seedcrm.crm.planorder.mapper.OrderRoleRecordMapper;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import com.seedcrm.crm.wecom.entity.CustomerWecomRelation;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchLogMapper;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.ClueItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CurrentRoleResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CustomerProfileResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CustomerSnapshotResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.DistributorBoardItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.EcomBindingResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.FinanceOverviewResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.OrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderWorkbenchResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.RoleRecordResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StaffRoleOptionResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.WecomLogResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.WithdrawRecordResponse;
import com.seedcrm.crm.workbench.service.StaffDirectoryService;
import com.seedcrm.crm.workbench.service.WorkbenchService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkbenchServiceImpl implements WorkbenchService {

    private final ClueMapper clueMapper;
    private final OrderMapper orderMapper;
    private final PlanOrderMapper planOrderMapper;
    private final OrderRoleRecordMapper orderRoleRecordMapper;
    private final CustomerMapper customerMapper;
    private final CustomerTagDetailMapper customerTagDetailMapper;
    private final CustomerEcomUserMapper customerEcomUserMapper;
    private final CustomerWecomRelationMapper customerWecomRelationMapper;
    private final WecomTouchLogMapper wecomTouchLogMapper;
    private final DistributorMapper distributorMapper;
    private final DistributorIncomeDetailMapper distributorIncomeDetailMapper;
    private final DistributorWithdrawMapper distributorWithdrawMapper;
    private final SalaryDetailMapper salaryDetailMapper;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final DistributorService distributorService;
    private final StaffDirectoryService staffDirectoryService;

    public WorkbenchServiceImpl(ClueMapper clueMapper,
                                OrderMapper orderMapper,
                                PlanOrderMapper planOrderMapper,
                                OrderRoleRecordMapper orderRoleRecordMapper,
                                CustomerMapper customerMapper,
                                CustomerTagDetailMapper customerTagDetailMapper,
                                CustomerEcomUserMapper customerEcomUserMapper,
                                CustomerWecomRelationMapper customerWecomRelationMapper,
                                WecomTouchLogMapper wecomTouchLogMapper,
                                DistributorMapper distributorMapper,
                                DistributorIncomeDetailMapper distributorIncomeDetailMapper,
                                DistributorWithdrawMapper distributorWithdrawMapper,
                                SalaryDetailMapper salaryDetailMapper,
                                WithdrawRecordMapper withdrawRecordMapper,
                                DistributorService distributorService,
                                StaffDirectoryService staffDirectoryService) {
        this.clueMapper = clueMapper;
        this.orderMapper = orderMapper;
        this.planOrderMapper = planOrderMapper;
        this.orderRoleRecordMapper = orderRoleRecordMapper;
        this.customerMapper = customerMapper;
        this.customerTagDetailMapper = customerTagDetailMapper;
        this.customerEcomUserMapper = customerEcomUserMapper;
        this.customerWecomRelationMapper = customerWecomRelationMapper;
        this.wecomTouchLogMapper = wecomTouchLogMapper;
        this.distributorMapper = distributorMapper;
        this.distributorIncomeDetailMapper = distributorIncomeDetailMapper;
        this.distributorWithdrawMapper = distributorWithdrawMapper;
        this.salaryDetailMapper = salaryDetailMapper;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.distributorService = distributorService;
        this.staffDirectoryService = staffDirectoryService;
    }

    @Override
    public List<ClueItemResponse> listClues(String sourceChannel, String productSourceType, String status) {
        List<Clue> clues = clueMapper.selectList(Wrappers.<Clue>lambdaQuery()
                .orderByDesc(Clue::getCreatedAt)
                .orderByDesc(Clue::getId));

        String normalizedSourceChannel = normalize(sourceChannel);
        String normalizedProductSourceType = normalize(productSourceType);
        String normalizedStatus = normalize(status);
        List<Clue> filteredClues = clues.stream()
                .filter(clue -> !StringUtils.hasText(normalizedSourceChannel)
                        || normalizedSourceChannel.equals(normalize(clue.getSourceChannel())))
                .filter(clue -> !StringUtils.hasText(normalizedProductSourceType)
                        || normalizedProductSourceType.equals(resolveProductSourceType(
                        clue.getSourceChannel(),
                        clue.getSource(),
                        clue.getRawData(),
                        clue.getSourceId(),
                        clue.getId())))
                .filter(clue -> !StringUtils.hasText(normalizedStatus)
                        || normalizedStatus.equals(normalize(clue.getStatus())))
                .limit(20)
                .toList();

        List<Long> clueIds = filteredClues.stream().map(Clue::getId).filter(Objects::nonNull).toList();
        Map<Long, Customer> customerByClueId = loadCustomerByClueId(clueIds);
        Map<Long, List<Order>> ordersByClueId = loadOrdersByClueId(clueIds);

        List<ClueItemResponse> responses = new ArrayList<>();
        for (Clue clue : filteredClues) {
            List<Order> clueOrders = ordersByClueId.getOrDefault(clue.getId(), List.of());
            Order latestOrder = clueOrders.stream()
                    .sorted(Comparator.comparing(Order::getCreateTime, Comparator.nullsLast(LocalDateTime::compareTo))
                            .reversed()
                            .thenComparing(Order::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                    .findFirst()
                    .orElse(null);
            Customer customer = customerByClueId.get(clue.getId());
            responses.add(new ClueItemResponse(
                    clue.getId(),
                    clue.getName(),
                    clue.getPhone(),
                    clue.getWechat(),
                    clue.getSourceChannel(),
                    resolveProductSourceType(clue.getSourceChannel(), clue.getSource(), clue.getRawData(), clue.getSourceId(), clue.getId()),
                    clue.getSourceId(),
                    clue.getStatus(),
                    clue.getCurrentOwnerId(),
                    staffDirectoryService.getUserName(clue.getCurrentOwnerId()),
                    clue.getIsPublic(),
                    resolveStoreName(clue.getId()),
                    customer == null ? null : customer.getId(),
                    latestOrder == null ? null : latestOrder.getId(),
                    latestOrder == null ? null : toWorkbenchOrderStatus(latestOrder.getStatus()),
                    (long) clueOrders.size(),
                    clue.getCreatedAt()));
        }
        return responses;
    }

    @Override
    public List<OrderItemResponse> listOrders(String status, String customerName, String customerPhone) {
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId));
        String normalizedStatus = normalize(status);
        String normalizedCustomerName = normalize(customerName);
        String normalizedCustomerPhone = normalize(customerPhone);
        Map<Long, Customer> customerMap = loadCustomers(orders.stream()
                .map(Order::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<Order> filteredOrders = orders.stream()
                .filter(order -> matchesWorkbenchOrderStatus(order, normalizedStatus))
                .filter(order -> matchesCustomerName(order, customerMap.get(order.getCustomerId()), normalizedCustomerName))
                .filter(order -> matchesCustomerPhone(order, customerMap.get(order.getCustomerId()), normalizedCustomerPhone))
                .limit(20)
                .toList();
        return buildOrderResponses(filteredOrders);
    }

    @Override
    public List<PlanOrderItemResponse> listPlanOrders(String status) {
        List<PlanOrder> planOrders = planOrderMapper.selectList(Wrappers.<PlanOrder>lambdaQuery()
                .orderByDesc(PlanOrder::getCreateTime)
                .orderByDesc(PlanOrder::getId));
        String normalizedStatus = normalize(status);
        List<PlanOrder> filteredPlanOrders = planOrders.stream()
                .filter(planOrder -> !StringUtils.hasText(normalizedStatus)
                        || normalizedStatus.equals(normalize(PlanOrderStatus.toApiValue(planOrder.getStatus()))))
                .toList();
        return buildPlanOrderSummaries(filteredPlanOrders);
    }

    @Override
    public PlanOrderWorkbenchResponse getPlanOrderWorkbench(Long planOrderId) {
        if (planOrderId == null || planOrderId <= 0) {
            throw new BusinessException("planOrderId is required");
        }
        PlanOrder planOrder = planOrderMapper.selectById(planOrderId);
        if (planOrder == null) {
            throw new BusinessException("plan order not found");
        }

        List<PlanOrderItemResponse> summaries = buildPlanOrderSummaries(List.of(planOrder));
        PlanOrderItemResponse summary = summaries.isEmpty() ? null : summaries.get(0);
        Order order = orderMapper.selectById(planOrder.getOrderId());
        Customer customer = order == null || order.getCustomerId() == null ? null : customerMapper.selectById(order.getCustomerId());
        CustomerWecomRelation wecomRelation = customer == null ? null : findWecomRelation(customer.getId());
        List<CustomerEcomUser> ecomUsers = customer == null ? List.of() : findEcomBindings(customer.getId());

        List<OrderRoleRecord> roleRecords = orderRoleRecordMapper.selectList(Wrappers.<OrderRoleRecord>lambdaQuery()
                .eq(OrderRoleRecord::getPlanOrderId, planOrderId)
                .orderByDesc(OrderRoleRecord::getCreateTime)
                .orderByDesc(OrderRoleRecord::getId));

        List<RoleRecordResponse> roleRecordResponses = roleRecords.stream()
                .map(this::buildRoleRecordResponse)
                .toList();
        List<CurrentRoleResponse> currentRoles = roleRecords.stream()
                .filter(record -> record.getIsCurrent() != null && record.getIsCurrent() == 1)
                .sorted(Comparator.comparing(record -> roleOrder(record.getRoleCode())))
                .map(this::buildCurrentRoleResponse)
                .toList();

        return new PlanOrderWorkbenchResponse(
                summary,
                buildOrderResponse(order, summary == null ? null : summary.getPlanOrderId(),
                        summary == null ? null : summary.getPlanOrderStatus(), customer),
                buildCustomerSnapshot(customer, wecomRelation, ecomUsers.size()),
                currentRoles,
                roleRecordResponses);
    }

    @Override
    public CustomerProfileResponse getCustomerProfile(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new BusinessException("customerId is required");
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("customer not found");
        }

        CustomerWecomRelation wecomRelation = findWecomRelation(customerId);
        List<CustomerEcomUser> ecomBindings = findEcomBindings(customerId);
        List<CustomerTagDetail> tagDetails = customerTagDetailMapper.selectList(Wrappers.<CustomerTagDetail>lambdaQuery()
                .eq(CustomerTagDetail::getCustomerId, customerId)
                .orderByAsc(CustomerTagDetail::getId));
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getCustomerId, customerId)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId));
        List<WecomTouchLog> wecomLogs = wecomTouchLogMapper.selectList(Wrappers.<WecomTouchLog>lambdaQuery()
                .eq(WecomTouchLog::getCustomerId, customerId)
                .orderByDesc(WecomTouchLog::getCreateTime)
                .orderByDesc(WecomTouchLog::getId)
                .last("LIMIT 10"));

        List<String> tagNames = new ArrayList<>();
        if (StringUtils.hasText(customer.getTag())) {
            tagNames.add(customer.getTag());
        }
        tagNames.addAll(tagDetails.stream()
                .map(detail -> StringUtils.hasText(detail.getTagName()) ? detail.getTagName() : detail.getTagCode())
                .filter(StringUtils::hasText)
                .filter(tag -> !tagNames.contains(tag))
                .toList());

        List<EcomBindingResponse> ecomBindingResponses = ecomBindings.stream()
                .map(binding -> new EcomBindingResponse(binding.getPlatform(), binding.getEcomUserId(), binding.getCreateTime()))
                .toList();
        List<WecomLogResponse> wecomLogResponses = wecomLogs.stream()
                .map(log -> new WecomLogResponse(log.getMessage(), log.getStatus(), log.getCreateTime()))
                .toList();

        return new CustomerProfileResponse(
                buildCustomerSnapshot(customer, wecomRelation, ecomBindings.size()),
                tagNames,
                buildOrderResponses(orders),
                ecomBindingResponses,
                wecomLogResponses);
    }

    @Override
    public List<DistributorBoardItemResponse> listDistributors() {
        List<Distributor> distributors = distributorMapper.selectList(Wrappers.<Distributor>lambdaQuery()
                .orderByDesc(Distributor::getCreateTime)
                .orderByDesc(Distributor::getId));
        List<DistributorBoardItemResponse> responses = new ArrayList<>();
        for (Distributor distributor : distributors) {
            DistributorStatsResponse stats = distributorService.getStats(distributor.getId());
            responses.add(new DistributorBoardItemResponse(
                    distributor.getId(),
                    distributor.getName(),
                    distributor.getContactInfo(),
                    distributor.getStatus(),
                    stats.getClueCount(),
                    stats.getDealCustomerCount(),
                    stats.getOrderCount(),
                    stats.getTotalIncome(),
                    stats.getSettledIncome(),
                    stats.getUnsettledIncome(),
                    stats.getWithdrawableAmount()));
        }
        return responses;
    }

    @Override
    public FinanceOverviewResponse getFinanceOverview() {
        LocalDate today = LocalDate.now();
        BigDecimal todayIncome = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                        .isNotNull(Order::getCompleteTime)
                        .orderByDesc(Order::getCompleteTime))
                .stream()
                .filter(order -> order.getCompleteTime() != null && today.equals(order.getCompleteTime().toLocalDate()))
                .map(Order::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal employeeIncome = salaryDetailMapper.selectList(Wrappers.<SalaryDetail>lambdaQuery()
                        .orderByDesc(SalaryDetail::getCreateTime))
                .stream()
                .map(SalaryDetail::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal distributorIncome = distributorIncomeDetailMapper.selectList(Wrappers.<DistributorIncomeDetail>lambdaQuery()
                        .orderByDesc(DistributorIncomeDetail::getCreateTime))
                .stream()
                .map(DistributorIncomeDetail::getIncomeAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, Distributor> distributorMap = distributorMapper.selectList(Wrappers.<Distributor>lambdaQuery())
                .stream()
                .collect(Collectors.toMap(Distributor::getId, Function.identity(), (left, right) -> left));

        List<WithdrawRecordResponse> withdrawRecords = new ArrayList<>();
        withdrawRecordMapper.selectList(Wrappers.<WithdrawRecord>lambdaQuery()
                        .orderByDesc(WithdrawRecord::getCreateTime)
                        .last("LIMIT 10"))
                .forEach(record -> withdrawRecords.add(new WithdrawRecordResponse(
                        "EMPLOYEE",
                        record.getUserId(),
                        staffDirectoryService.getUserName(record.getUserId()),
                        scale(record.getAmount()),
                        record.getStatus(),
                        record.getCreateTime())));

        distributorWithdrawMapper.selectList(Wrappers.<DistributorWithdraw>lambdaQuery()
                        .orderByDesc(DistributorWithdraw::getCreateTime)
                        .last("LIMIT 10"))
                .forEach(record -> withdrawRecords.add(new WithdrawRecordResponse(
                        "DISTRIBUTOR",
                        record.getDistributorId(),
                        distributorMap.containsKey(record.getDistributorId())
                                ? distributorMap.get(record.getDistributorId()).getName()
                                : "分销商#" + record.getDistributorId(),
                        scale(record.getAmount()),
                        record.getStatus(),
                        record.getCreateTime())));

        List<WithdrawRecordResponse> recentWithdraws = withdrawRecords.stream()
                .sorted(Comparator.comparing(WithdrawRecordResponse::getCreateTime, Comparator.nullsLast(LocalDateTime::compareTo))
                        .reversed())
                .limit(10)
                .toList();

        return new FinanceOverviewResponse(
                scale(todayIncome),
                scale(employeeIncome),
                scale(distributorIncome),
                recentWithdraws);
    }

    @Override
    public List<StaffRoleOptionResponse> listStaffOptions() {
        return staffDirectoryService.listRoleOptions();
    }

    private List<OrderItemResponse> buildOrderResponses(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> customerIds = orders.stream().map(Order::getCustomerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Customer> customerMap = loadCustomers(customerIds);
        Map<Long, PlanOrder> planOrderByOrderId = loadPlanOrderByOrderId(orders.stream().map(Order::getId).toList());

        return orders.stream()
                .map(order -> {
                    Customer customer = order.getCustomerId() == null ? null : customerMap.get(order.getCustomerId());
                    PlanOrder planOrder = planOrderByOrderId.get(order.getId());
                    return buildOrderResponse(order,
                            planOrder == null ? null : planOrder.getId(),
                            planOrder == null ? null : planOrder.getStatus(),
                            customer);
                })
                .toList();
    }

    private OrderItemResponse buildOrderResponse(Order order, Long planOrderId, String planOrderStatus, Customer customer) {
        if (order == null) {
            return null;
        }
        return new OrderItemResponse(
                order.getId(),
                order.getOrderNo(),
                order.getClueId(),
                order.getCustomerId(),
                customer == null ? null : customer.getName(),
                customer == null ? null : customer.getPhone(),
                order.getSourceChannel(),
                resolveProductSourceType(order.getSourceChannel(), null, null, order.getSourceId(), order.getClueId()),
                resolveStoreName(order.getClueId() == null ? order.getId() : order.getClueId()),
                scale(order.getAmount()),
                scale(order.getDeposit()),
                OrderType.toApiValue(order.getType()),
                toWorkbenchOrderStatus(order.getStatus()),
                OrderStatus.toApiValue(order.getStatus()),
                planOrderId,
                PlanOrderStatus.toApiValue(planOrderStatus),
                order.getAppointmentTime(),
                order.getArriveTime(),
                order.getCompleteTime(),
                order.getRemark(),
                order.getServiceDetailJson(),
                StringUtils.hasText(order.getVerificationStatus()) ? order.getVerificationStatus() : "UNVERIFIED",
                order.getVerificationMethod(),
                order.getVerificationCode(),
                order.getVerificationTime(),
                order.getVerificationOperatorId(),
                order.getCreateTime());
    }

    private boolean matchesCustomerName(Order order, Customer customer, String normalizedCustomerName) {
        if (!StringUtils.hasText(normalizedCustomerName)) {
            return true;
        }
        String target = normalize(customer == null ? null : customer.getName());
        return target.contains(normalizedCustomerName);
    }

    private boolean matchesCustomerPhone(Order order, Customer customer, String normalizedCustomerPhone) {
        if (!StringUtils.hasText(normalizedCustomerPhone)) {
            return true;
        }
        String target = normalize(customer == null ? null : customer.getPhone());
        return target.contains(normalizedCustomerPhone);
    }

    private List<PlanOrderItemResponse> buildPlanOrderSummaries(List<PlanOrder> planOrders) {
        if (planOrders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = planOrders.stream().map(PlanOrder::getOrderId).filter(Objects::nonNull).toList();
        Map<Long, Order> orderMap = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                        .in(Order::getId, orderIds))
                .stream()
                .collect(Collectors.toMap(Order::getId, Function.identity(), (left, right) -> left));
        List<Long> customerIds = orderMap.values().stream().map(Order::getCustomerId).filter(Objects::nonNull).toList();
        Map<Long, Customer> customerMap = loadCustomers(customerIds);
        Map<Long, CustomerWecomRelation> wecomMap = loadWecomRelationMap(customerIds);
        Map<Long, Integer> ecomCountMap = loadEcomCountMap(customerIds);
        Map<Long, List<OrderRoleRecord>> currentRolesByPlanOrderId = loadCurrentRoleRecordMap(
                planOrders.stream().map(PlanOrder::getId).toList());

        return planOrders.stream()
                .map(planOrder -> {
                    Order order = orderMap.get(planOrder.getOrderId());
                    Customer customer = order == null || order.getCustomerId() == null
                            ? null
                            : customerMap.get(order.getCustomerId());
                    List<CurrentRoleResponse> currentRoles = currentRolesByPlanOrderId
                            .getOrDefault(planOrder.getId(), List.of())
                            .stream()
                            .sorted(Comparator.comparing(record -> roleOrder(record.getRoleCode())))
                            .map(this::buildCurrentRoleResponse)
                            .toList();
                    CustomerSnapshotResponse customerSnapshot = customer == null
                            ? null
                            : buildCustomerSnapshot(customer,
                            wecomMap.get(customer.getId()),
                            ecomCountMap.getOrDefault(customer.getId(), 0));
                    return new PlanOrderItemResponse(
                            planOrder.getId(),
                            PlanOrderStatus.toApiValue(planOrder.getStatus()),
                            order == null ? null : order.getId(),
                            order == null ? null : order.getOrderNo(),
                            order == null ? null : OrderStatus.toApiValue(order.getStatus()),
                            order == null ? scale(null) : scale(order.getAmount()),
                            order == null ? null : order.getSourceChannel(),
                            customerSnapshot,
                            currentRoles,
                            planOrder.getArriveTime(),
                            planOrder.getStartTime(),
                            planOrder.getFinishTime(),
                            planOrder.getCreateTime());
                })
                .toList();
    }

    private CurrentRoleResponse buildCurrentRoleResponse(OrderRoleRecord record) {
        return new CurrentRoleResponse(
                normalize(record.getRoleCode()),
                staffDirectoryService.getRoleName(record.getRoleCode()),
                record.getUserId(),
                staffDirectoryService.getUserName(record.getUserId()),
                record.getStartTime());
    }

    private RoleRecordResponse buildRoleRecordResponse(OrderRoleRecord record) {
        return new RoleRecordResponse(
                record.getId(),
                normalize(record.getRoleCode()),
                staffDirectoryService.getRoleName(record.getRoleCode()),
                record.getUserId(),
                staffDirectoryService.getUserName(record.getUserId()),
                record.getIsCurrent(),
                record.getStartTime(),
                record.getEndTime(),
                record.getCreateTime());
    }

    private CustomerSnapshotResponse buildCustomerSnapshot(Customer customer,
                                                           CustomerWecomRelation wecomRelation,
                                                           int ecomBindingCount) {
        if (customer == null) {
            return null;
        }
        return new CustomerSnapshotResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getWechat(),
                customer.getSourceChannel(),
                customer.getStatus(),
                customer.getTag(),
                wecomRelation != null && StringUtils.hasText(wecomRelation.getExternalUserid()),
                wecomRelation == null ? null : wecomRelation.getExternalUserid(),
                wecomRelation == null ? null : wecomRelation.getWecomUserId(),
                ecomBindingCount);
    }

    private Map<Long, Customer> loadCustomers(List<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return customerMapper.selectList(Wrappers.<Customer>lambdaQuery()
                        .in(Customer::getId, customerIds))
                .stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Customer> loadCustomerByClueId(List<Long> clueIds) {
        if (clueIds.isEmpty()) {
            return Map.of();
        }
        return customerMapper.selectList(Wrappers.<Customer>lambdaQuery()
                        .in(Customer::getSourceClueId, clueIds))
                .stream()
                .filter(customer -> customer.getSourceClueId() != null)
                .collect(Collectors.toMap(Customer::getSourceClueId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, List<Order>> loadOrdersByClueId(List<Long> clueIds) {
        if (clueIds.isEmpty()) {
            return Map.of();
        }
        return orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                        .in(Order::getClueId, clueIds))
                .stream()
                .filter(order -> order.getClueId() != null)
                .collect(Collectors.groupingBy(Order::getClueId));
    }

    private Map<Long, PlanOrder> loadPlanOrderByOrderId(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return planOrderMapper.selectList(Wrappers.<PlanOrder>lambdaQuery()
                        .in(PlanOrder::getOrderId, orderIds))
                .stream()
                .filter(planOrder -> planOrder.getOrderId() != null)
                .collect(Collectors.toMap(PlanOrder::getOrderId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, CustomerWecomRelation> loadWecomRelationMap(Collection<Long> customerIds) {
        List<Long> filteredIds = customerIds.stream().filter(Objects::nonNull).distinct().toList();
        if (filteredIds.isEmpty()) {
            return Map.of();
        }
        return customerWecomRelationMapper.selectList(Wrappers.<CustomerWecomRelation>lambdaQuery()
                        .in(CustomerWecomRelation::getCustomerId, filteredIds))
                .stream()
                .filter(relation -> relation.getCustomerId() != null)
                .collect(Collectors.toMap(CustomerWecomRelation::getCustomerId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Integer> loadEcomCountMap(Collection<Long> customerIds) {
        List<Long> filteredIds = customerIds.stream().filter(Objects::nonNull).distinct().toList();
        if (filteredIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> counts = new HashMap<>();
        customerEcomUserMapper.selectList(Wrappers.<CustomerEcomUser>lambdaQuery()
                        .in(CustomerEcomUser::getCustomerId, filteredIds))
                .forEach(binding -> counts.merge(binding.getCustomerId(), 1, Integer::sum));
        return counts;
    }

    private Map<Long, List<OrderRoleRecord>> loadCurrentRoleRecordMap(List<Long> planOrderIds) {
        if (planOrderIds.isEmpty()) {
            return Map.of();
        }
        return orderRoleRecordMapper.selectList(Wrappers.<OrderRoleRecord>lambdaQuery()
                        .in(OrderRoleRecord::getPlanOrderId, planOrderIds)
                        .eq(OrderRoleRecord::getIsCurrent, 1))
                .stream()
                .filter(record -> record.getPlanOrderId() != null)
                .collect(Collectors.groupingBy(OrderRoleRecord::getPlanOrderId, LinkedHashMap::new, Collectors.toList()));
    }

    private CustomerWecomRelation findWecomRelation(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return customerWecomRelationMapper.selectOne(Wrappers.<CustomerWecomRelation>lambdaQuery()
                .eq(CustomerWecomRelation::getCustomerId, customerId)
                .last("LIMIT 1"));
    }

    private List<CustomerEcomUser> findEcomBindings(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        return customerEcomUserMapper.selectList(Wrappers.<CustomerEcomUser>lambdaQuery()
                .eq(CustomerEcomUser::getCustomerId, customerId)
                .orderByDesc(CustomerEcomUser::getCreateTime)
                .orderByDesc(CustomerEcomUser::getId));
    }

    private int roleOrder(String roleCode) {
        return switch (normalize(roleCode)) {
            case "CONSULTANT" -> 1;
            case "DOCTOR" -> 2;
            case "ASSISTANT" -> 3;
            default -> 99;
        };
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveProductSourceType(String sourceChannel,
                                            String legacySource,
                                            String rawData,
                                            Long sourceId,
                                            Long fallbackId) {
        String rawText = ((legacySource == null ? "" : legacySource) + " " + (rawData == null ? "" : rawData))
                .toUpperCase(Locale.ROOT);
        if (rawText.contains("FORM") || rawText.contains("表单")) {
            return "FORM";
        }
        if (rawText.contains("GROUP") || rawText.contains("COUPON") || rawText.contains("团购")) {
            return "GROUP_BUY";
        }
        String normalizedSourceChannel = normalize(sourceChannel);
        if ("DISTRIBUTOR".equals(normalizedSourceChannel) || "DISTRIBUTION".equals(normalizedSourceChannel)) {
            return "FORM";
        }
        long compatibleMarker = sourceId != null ? sourceId : (fallbackId == null ? 0L : fallbackId);
        return compatibleMarker % 2 == 0 ? "FORM" : "GROUP_BUY";
    }

    private String resolveStoreName(Long compatibleId) {
        long marker = compatibleId == null ? 0L : Math.abs(compatibleId);
        return switch ((int) (marker % 3)) {
            case 0 -> "静安门店";
            case 1 -> "浦东门店";
            default -> "徐汇门店";
        };
    }

    private boolean matchesWorkbenchOrderStatus(Order order, String normalizedStatus) {
        if (!StringUtils.hasText(normalizedStatus)) {
            return true;
        }
        String orderStatus = normalize(order == null ? null : order.getStatus());
        if (!StringUtils.hasText(orderStatus)) {
            return false;
        }
        return switch (normalizedStatus) {
            case "PAID" -> List.of("PAID_DEPOSIT", "APPOINTMENT", "ARRIVED", "SERVING").contains(orderStatus);
            case "USED", "FINISHED", "COMPLETED" -> "COMPLETED".equals(orderStatus);
            case "APPOINTMENT" -> "APPOINTMENT".equals(orderStatus);
            case "ARRIVED" -> "ARRIVED".equals(orderStatus);
            case "SERVING" -> "SERVING".equals(orderStatus);
            case "CREATED" -> "CREATED".equals(orderStatus);
            case "CANCELLED" -> "CANCELLED".equals(orderStatus);
            case "REFUNDED" -> "REFUNDED".equals(orderStatus);
            default -> normalizedStatus.equals(orderStatus);
        };
    }

    private String toWorkbenchOrderStatus(String status) {
        String normalizedStatus = normalize(status);
        return StringUtils.hasText(normalizedStatus) ? normalizedStatus.toLowerCase(Locale.ROOT) : null;
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
