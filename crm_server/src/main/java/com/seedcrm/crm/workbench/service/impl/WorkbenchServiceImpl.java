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
import com.seedcrm.crm.order.entity.OrderActionRecord;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.order.mapper.OrderActionRecordMapper;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
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
import com.seedcrm.crm.wecom.entity.WecomAppConfig;
import com.seedcrm.crm.wecom.entity.WecomLiveCodeConfig;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchLogMapper;
import com.seedcrm.crm.wecom.service.WecomConsoleService;
import com.seedcrm.crm.wecom.support.WecomBindingStateCodec;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.ClueItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.AppointmentRecordResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CurrentRoleResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CustomerProfileResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CustomerSnapshotResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.DistributorBoardItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.EcomBindingResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.FinanceMonthlyStatResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.FinanceOverviewResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.FinanceTeamStatResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.FlowTraceItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.OrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderWorkbenchResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.RoleRecordResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StaffRoleOptionResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StoreLiveCodePreviewResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.WecomLogResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.WithdrawRecordResponse;
import com.seedcrm.crm.workbench.service.StaffDirectoryService;
import com.seedcrm.crm.workbench.service.WorkbenchService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class WorkbenchServiceImpl implements WorkbenchService {

    private final ClueMapper clueMapper;
    private final OrderMapper orderMapper;
    private final OrderActionRecordMapper orderActionRecordMapper;
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
    private final WecomConsoleService wecomConsoleService;
    private final JdbcTemplate jdbcTemplate;

    public WorkbenchServiceImpl(ClueMapper clueMapper,
                                OrderMapper orderMapper,
                                OrderActionRecordMapper orderActionRecordMapper,
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
                                 StaffDirectoryService staffDirectoryService,
                                 WecomConsoleService wecomConsoleService,
                                 DataSource dataSource) {
        this.clueMapper = clueMapper;
        this.orderMapper = orderMapper;
        this.orderActionRecordMapper = orderActionRecordMapper;
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
        this.wecomConsoleService = wecomConsoleService;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
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
                    resolveVisibleStoreName(clue.getId()),
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
    public StoreLiveCodePreviewResponse getOrderLiveCodePreview(Long orderId, PermissionRequestContext context) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException("orderId is required");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        Long currentUserId = context == null ? null : context.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("当前登录人信息缺失，无法生成专属企微活码");
        }
        Customer customer = order.getCustomerId() == null ? null : customerMapper.selectById(order.getCustomerId());
        if (customer == null) {
            throw new BusinessException("订单未绑定客户，无法生成专属企微活码");
        }
        String bindingUserPhone = staffDirectoryService.getUserPhone(currentUserId);
        if (!StringUtils.hasText(bindingUserPhone)) {
            throw new BusinessException("当前账号未维护手机号，无法生成专属企微活码，请联系管理员完善员工手机号");
        }
        String storeName = resolveOrderStoreName(order);
        return wecomConsoleService.listLiveCodeConfigs().stream()
                .filter(config -> config != null && config.getIsEnabled() != null && config.getIsEnabled() == 1)
                .filter(config -> config.getPublishedAt() != null)
                .filter(config -> StringUtils.hasText(config.getQrCodeUrl()))
                .filter(config -> config.getStoreNames() != null && config.getStoreNames().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .anyMatch(storeName::equals))
                .sorted(Comparator
                        .comparing(WecomLiveCodeConfig::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WecomLiveCodeConfig::getGeneratedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WecomLiveCodeConfig::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(config -> buildOrderLiveCodePreview(config, order, customer, storeName, currentUserId, bindingUserPhone))
                .findFirst()
                .orElse(null);
    }

    private StoreLiveCodePreviewResponse buildOrderLiveCodePreview(WecomLiveCodeConfig config,
                                                                   Order order,
                                                                   Customer customer,
                                                                   String storeName,
                                                                   Long currentUserId,
                                                                   String bindingUserPhone) {
        String bindingState = WecomBindingStateCodec.encode(order.getId(), currentUserId, customer.getId(), bindingUserPhone);
        String bindingUserName = staffDirectoryService.getUserName(currentUserId);
        BindingQr bindingQr = resolveBindingQr(config, bindingState, currentUserId);
        return new StoreLiveCodePreviewResponse(
                config.getId(),
                config.getCodeName(),
                storeName,
                bindingQr.contactWayId(),
                bindingQr.qrCodeUrl(),
                bindingQr.shortLink(),
                bindingState,
                currentUserId,
                bindingUserName,
                bindingUserPhone,
                bindingQr.status(),
                bindingQr.message(),
                config.getStoreNames(),
                config.getGeneratedAt(),
                config.getPublishedAt());
    }

    private BindingQr resolveBindingQr(WecomLiveCodeConfig config, String bindingState, Long currentUserId) {
        WecomAppConfig wecomConfig = wecomConsoleService.getConfig();
        boolean useLiveMode = wecomConfig != null
                && wecomConfig.getEnabled() != null
                && wecomConfig.getEnabled() == 1
                && "LIVE".equalsIgnoreCase(wecomConfig.getExecutionMode());
        if (!useLiveMode) {
            String shortLink = appendQueryParameter(
                    "/wecom/mock-contact/" + normalizeContactWayId(config.getContactWayId()),
                    "state",
                    bindingState);
            return new BindingQr(
                    config.getContactWayId(),
                    config.getQrCodeUrl(),
                    shortLink,
                    "MOCK_READY",
                    "模拟模式已生成系统联调二维码，扫码会走本系统 MOCK 回调完成绑定");
        }

        String wecomAccount = staffDirectoryService.getWecomAccount(currentUserId);
        if (!StringUtils.hasText(wecomAccount)) {
            return new BindingQr(
                    config.getContactWayId(),
                    null,
                    null,
                    "MISSING_ACCOUNT",
                    "当前账号未维护企业微信账号，无法生成订单专属企微活码");
        }
        try {
            String accessToken = wecomConsoleService.resolveAccessToken(wecomConfig);
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("type", wecomConfig.getLiveCodeType() == null ? 2 : wecomConfig.getLiveCodeType());
            request.put("scene", wecomConfig.getLiveCodeScene() == null ? 2 : wecomConfig.getLiveCodeScene());
            request.put("style", wecomConfig.getLiveCodeStyle() == null ? 1 : wecomConfig.getLiveCodeStyle());
            request.put("remark", config.getCodeName() + "-订单绑定");
            request.put("skip_verify", wecomConfig.getSkipVerify() == null || wecomConfig.getSkipVerify() == 1);
            request.put("state", bindingState);
            request.put("user", List.of(wecomAccount));
            com.fasterxml.jackson.databind.JsonNode response = RestClient.create()
                    .post()
                    .uri("https://qyapi.weixin.qq.com/cgi-bin/externalcontact/add_contact_way?access_token={token}", accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(com.fasterxml.jackson.databind.JsonNode.class);
            int errorCode = response == null ? -1 : response.path("errcode").asInt(-1);
            if (errorCode != 0) {
                String message = response == null ? "企业微信专属活码生成失败" : response.path("errmsg").asText("企业微信专属活码生成失败");
                return new BindingQr(config.getContactWayId(), null, null, "LIVE_FALLBACK", message);
            }
            String contactWayId = extractText(response, "config_id", "contact_way_id");
            String qrCodeUrl = extractText(response, "qr_code", "data.qr_code");
            return new BindingQr(contactWayId, qrCodeUrl, qrCodeUrl, "LIVE_READY", "真实模式已生成带 state 的订单专属活码");
        } catch (Exception exception) {
            return new BindingQr(
                    config.getContactWayId(),
                    null,
                    null,
                    "LIVE_FALLBACK",
                    "专属活码生成失败：" + exception.getMessage());
        }
    }

    private String normalizeContactWayId(String contactWayId) {
        return StringUtils.hasText(contactWayId) ? contactWayId.trim() : "mock";
    }

    private String appendQueryParameter(String url, String key, String value) {
        String separator = StringUtils.hasText(url) && url.contains("?") ? "&" : "?";
        return (StringUtils.hasText(url) ? url.trim() : "")
                + separator
                + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
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

        OrderItemResponse orderResponse = buildOrderResponse(order, summary == null ? null : summary.getPlanOrderId(),
                summary == null ? null : summary.getPlanOrderStatus(), customer);
        if (orderResponse != null && order != null && order.getId() != null) {
            orderResponse.setAppointmentRecords(loadAppointmentRecordsByOrderId(List.of(order.getId()))
                    .getOrDefault(order.getId(), List.of()));
        }

        Long orderId = order == null ? null : order.getId();
        List<FlowTraceItemResponse> flowTrace = loadOrderFlowTrace(orderId);
        flowTrace = mergeServiceFormActionTrace(orderId, flowTrace);

        return new PlanOrderWorkbenchResponse(
                summary,
                orderResponse,
                buildCustomerSnapshot(customer, wecomRelation, ecomUsers.size()),
                currentRoles,
                roleRecordResponses,
                flowTrace);
    }

    private List<FlowTraceItemResponse> loadOrderFlowTrace(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return List.of();
        }
        try {
            Long instanceId = jdbcTemplate.query("""
                    SELECT id
                    FROM system_flow_instance
                    WHERE flow_code = 'ORDER_MAIN_FLOW'
                      AND business_object = 'ORDER'
                      AND business_id = ?
                    LIMIT 1
                    """, rs -> rs.next() ? rs.getLong("id") : null, orderId);
            if (instanceId == null) {
                return List.of();
            }
            return jdbcTemplate.query("""
                    SELECT action_code, from_node_code, to_node_code, summary, actor_role_code, event_time
                    FROM system_flow_event_log
                    WHERE instance_id = ?
                    ORDER BY event_time ASC, id ASC
                    """, (rs, rowNum) -> {
                return new FlowTraceItemResponse(
                        rs.getString("action_code"),
                        rs.getString("from_node_code"),
                        rs.getString("to_node_code"),
                        rs.getString("summary"),
                        rs.getString("actor_role_code"),
                        rs.getTimestamp("event_time") == null ? null : rs.getTimestamp("event_time").toLocalDateTime());
            }, instanceId);
        } catch (DataAccessException exception) {
            return List.of();
        }
    }

    private List<FlowTraceItemResponse> mergeServiceFormActionTrace(Long orderId, List<FlowTraceItemResponse> flowTrace) {
        List<FlowTraceItemResponse> merged = new ArrayList<>(flowTrace == null ? List.of() : flowTrace);
        if (orderId == null || orderId <= 0) {
            return merged;
        }
        Set<String> existingActionCodes = merged.stream()
                .map(FlowTraceItemResponse::getActionCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<OrderActionRecord> records = orderActionRecordMapper.selectList(Wrappers.<OrderActionRecord>lambdaQuery()
                .eq(OrderActionRecord::getOrderId, orderId)
                .in(OrderActionRecord::getActionType, List.of("SERVICE_FORM_PRINT", "SERVICE_FORM_CONFIRM"))
                .orderByAsc(OrderActionRecord::getCreateTime)
                .orderByAsc(OrderActionRecord::getId));
        records.stream()
                .filter(record -> !existingActionCodes.contains(record.getActionType()))
                .map(record -> new FlowTraceItemResponse(
                        record.getActionType(),
                        record.getFromStatus(),
                        record.getToStatus(),
                        record.getRemark(),
                        null,
                        record.getCreateTime()))
                .forEach(merged::add);
        merged.sort(Comparator.comparing(FlowTraceItemResponse::getEventTime, Comparator.nullsLast(LocalDateTime::compareTo)));
        return merged;
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
        List<Order> completedOrders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                        .isNotNull(Order::getCompleteTime)
                        .orderByDesc(Order::getCompleteTime));
        BigDecimal todayIncome = completedOrders.stream()
                .filter(order -> order.getCompleteTime() != null && today.equals(order.getCompleteTime().toLocalDate()))
                .map(Order::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SalaryDetail> salaryDetails = salaryDetailMapper.selectList(Wrappers.<SalaryDetail>lambdaQuery()
                .orderByDesc(SalaryDetail::getCreateTime));
        BigDecimal employeeIncome = salaryDetails.stream()
                .map(SalaryDetail::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DistributorIncomeDetail> distributorIncomeDetails = distributorIncomeDetailMapper.selectList(
                Wrappers.<DistributorIncomeDetail>lambdaQuery().orderByDesc(DistributorIncomeDetail::getCreateTime));
        BigDecimal distributorIncome = distributorIncomeDetails.stream()
                .map(DistributorIncomeDetail::getIncomeAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<WithdrawRecord> employeeWithdraws = withdrawRecordMapper.selectList(Wrappers.<WithdrawRecord>lambdaQuery()
                .orderByDesc(WithdrawRecord::getCreateTime));
        List<DistributorWithdraw> distributorWithdraws = distributorWithdrawMapper.selectList(Wrappers.<DistributorWithdraw>lambdaQuery()
                .orderByDesc(DistributorWithdraw::getCreateTime));

        Map<Long, Distributor> distributorMap = distributorMapper.selectList(Wrappers.<Distributor>lambdaQuery())
                .stream()
                .collect(Collectors.toMap(Distributor::getId, Function.identity(), (left, right) -> left));

        List<WithdrawRecordResponse> withdrawRecords = new ArrayList<>();
        employeeWithdraws.stream()
                .limit(10)
                .forEach(record -> withdrawRecords.add(new WithdrawRecordResponse(
                        "EMPLOYEE",
                        record.getUserId(),
                        staffDirectoryService.getUserName(record.getUserId()),
                        scale(record.getAmount()),
                        record.getStatus(),
                        record.getCreateTime())));

        distributorWithdraws.stream()
                .limit(10)
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
        List<FinanceMonthlyStatResponse> monthlyStats = buildMonthlyFinanceStats(
                completedOrders,
                salaryDetails,
                distributorIncomeDetails,
                employeeWithdraws,
                distributorWithdraws
        );
        List<FinanceTeamStatResponse> teamStats = buildTeamFinanceStats(salaryDetails, distributorIncomeDetails);

        return new FinanceOverviewResponse(
                scale(todayIncome),
                scale(employeeIncome),
                scale(distributorIncome),
                recentWithdraws,
                monthlyStats,
                teamStats);
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
        Map<Long, List<AppointmentRecordResponse>> appointmentRecordsByOrderId =
                loadAppointmentRecordsByOrderId(orders.stream().map(Order::getId).filter(Objects::nonNull).toList());

        return orders.stream()
                .map(order -> {
                    Customer customer = order.getCustomerId() == null ? null : customerMap.get(order.getCustomerId());
                    PlanOrder planOrder = planOrderByOrderId.get(order.getId());
                    OrderItemResponse response = buildOrderResponse(order,
                            planOrder == null ? null : planOrder.getId(),
                            planOrder == null ? null : planOrder.getStatus(),
                            customer);
                    response.setAppointmentRecords(appointmentRecordsByOrderId.getOrDefault(order.getId(), List.of()));
                    return response;
                })
                .toList();
    }

    private Map<Long, List<AppointmentRecordResponse>> loadAppointmentRecordsByOrderId(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        List<OrderActionRecord> records = orderActionRecordMapper.selectList(Wrappers.<OrderActionRecord>lambdaQuery()
                .in(OrderActionRecord::getOrderId, orderIds)
                .in(OrderActionRecord::getActionType, List.of(
                        "APPOINTMENT_CREATE",
                        "APPOINTMENT_CHANGE",
                        "APPOINTMENT_CANCEL"))
                .orderByDesc(OrderActionRecord::getCreateTime)
                .orderByDesc(OrderActionRecord::getId));
        return records.stream()
                .collect(Collectors.groupingBy(
                        OrderActionRecord::getOrderId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::buildAppointmentRecordResponse, Collectors.toList())));
    }

    private AppointmentRecordResponse buildAppointmentRecordResponse(OrderActionRecord record) {
        return new AppointmentRecordResponse(
                record.getActionType(),
                record.getFromStatus(),
                record.getToStatus(),
                record.getOperatorUserId(),
                staffDirectoryService.getUserName(record.getOperatorUserId()),
                record.getRemark(),
                record.getExtraJson(),
                record.getCreateTime());
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
                resolveOrderStoreName(order),
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
                List.of(),
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

    private String extractText(com.fasterxml.jackson.databind.JsonNode node, String... candidates) {
        if (node == null || candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            com.fasterxml.jackson.databind.JsonNode current = node;
            for (String part : candidate.split("\\.")) {
                if (current == null) {
                    break;
                }
                current = current.path(part);
            }
            if (current != null && !current.isMissingNode() && !current.isNull() && StringUtils.hasText(current.asText())) {
                return current.asText().trim();
            }
        }
        return null;
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

    private String resolveVisibleStoreName(Long compatibleId) {
        long marker = compatibleId == null ? 0L : Math.abs(compatibleId);
        return switch ((int) (marker % 3)) {
            case 0 -> "静安门店";
            case 1 -> "浦东门店";
            default -> "徐汇门店";
        };
    }

    private String resolveOrderStoreName(Order order) {
        if (order == null) {
            return null;
        }
        if (StringUtils.hasText(order.getAppointmentStoreName())) {
            return order.getAppointmentStoreName().trim();
        }
        return resolveVisibleStoreName(order.getClueId() == null ? order.getId() : order.getClueId());
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

    private List<FinanceMonthlyStatResponse> buildMonthlyFinanceStats(List<Order> completedOrders,
                                                                      List<SalaryDetail> salaryDetails,
                                                                      List<DistributorIncomeDetail> distributorIncomeDetails,
                                                                      List<WithdrawRecord> employeeWithdraws,
                                                                      List<DistributorWithdraw> distributorWithdraws) {
        YearMonth currentMonth = YearMonth.now();
        Map<YearMonth, MonthlyFinanceBucket> buckets = new LinkedHashMap<>();
        for (int offset = 5; offset >= 0; offset--) {
            buckets.put(currentMonth.minusMonths(offset), new MonthlyFinanceBucket());
        }

        completedOrders.forEach(order -> addMonthlyAmount(
                buckets,
                order == null ? null : order.getCompleteTime(),
                order == null ? null : order.getAmount(),
                MonthlyMetric.ORDER
        ));
        salaryDetails.forEach(detail -> addMonthlyAmount(
                buckets,
                detail == null ? null : detail.getCreateTime(),
                detail == null ? null : detail.getAmount(),
                MonthlyMetric.EMPLOYEE
        ));
        distributorIncomeDetails.forEach(detail -> addMonthlyAmount(
                buckets,
                detail == null ? null : detail.getCreateTime(),
                detail == null ? null : detail.getIncomeAmount(),
                MonthlyMetric.DISTRIBUTOR
        ));
        employeeWithdraws.forEach(record -> addMonthlyAmount(
                buckets,
                record == null ? null : record.getCreateTime(),
                record == null ? null : record.getAmount(),
                MonthlyMetric.WITHDRAW
        ));
        distributorWithdraws.forEach(record -> addMonthlyAmount(
                buckets,
                record == null ? null : record.getCreateTime(),
                record == null ? null : record.getAmount(),
                MonthlyMetric.WITHDRAW
        ));

        return buckets.entrySet().stream()
                .map(entry -> new FinanceMonthlyStatResponse(
                        entry.getKey().toString(),
                        scale(entry.getValue().orderIncome),
                        scale(entry.getValue().employeeIncome),
                        scale(entry.getValue().distributorIncome),
                        scale(entry.getValue().withdrawAmount)))
                .toList();
    }

    private void addMonthlyAmount(Map<YearMonth, MonthlyFinanceBucket> buckets,
                                  LocalDateTime dateTime,
                                  BigDecimal amount,
                                  MonthlyMetric metric) {
        if (dateTime == null || amount == null) {
            return;
        }
        MonthlyFinanceBucket bucket = buckets.get(YearMonth.from(dateTime));
        if (bucket == null) {
            return;
        }
        switch (metric) {
            case ORDER -> bucket.orderIncome = bucket.orderIncome.add(amount);
            case EMPLOYEE -> bucket.employeeIncome = bucket.employeeIncome.add(amount);
            case DISTRIBUTOR -> bucket.distributorIncome = bucket.distributorIncome.add(amount);
            case WITHDRAW -> bucket.withdrawAmount = bucket.withdrawAmount.add(amount);
        }
    }

    private List<FinanceTeamStatResponse> buildTeamFinanceStats(List<SalaryDetail> salaryDetails,
                                                                List<DistributorIncomeDetail> distributorIncomeDetails) {
        Map<String, TeamFinanceBucket> buckets = new LinkedHashMap<>();
        for (SalaryDetail detail : salaryDetails) {
            String roleCode = normalize(detail == null ? null : detail.getRoleCode());
            if (!StringUtils.hasText(roleCode)) {
                continue;
            }
            TeamFinanceBucket bucket = buckets.computeIfAbsent(roleCode, key -> new TeamFinanceBucket(staffDirectoryService.getRoleName(key)));
            if (detail.getUserId() != null) {
                bucket.memberIds.put(detail.getUserId(), Boolean.TRUE);
            }
            bucket.serviceCount++;
            bucket.orderIncome = bucket.orderIncome.add(detail.getOrderAmount() == null ? BigDecimal.ZERO : detail.getOrderAmount());
            bucket.incomeAmount = bucket.incomeAmount.add(detail.getAmount() == null ? BigDecimal.ZERO : detail.getAmount());
        }
        if (!distributorIncomeDetails.isEmpty()) {
            TeamFinanceBucket distributorBucket = buckets.computeIfAbsent("DISTRIBUTOR_TEAM", key -> new TeamFinanceBucket("DISTRIBUTOR_TEAM"));
            for (DistributorIncomeDetail detail : distributorIncomeDetails) {
                if (detail.getDistributorId() != null) {
                    distributorBucket.memberIds.put(detail.getDistributorId(), Boolean.TRUE);
                }
                distributorBucket.serviceCount++;
                distributorBucket.orderIncome = distributorBucket.orderIncome.add(detail.getOrderAmount() == null ? BigDecimal.ZERO : detail.getOrderAmount());
                distributorBucket.incomeAmount = distributorBucket.incomeAmount.add(detail.getIncomeAmount() == null ? BigDecimal.ZERO : detail.getIncomeAmount());
            }
        }
        return buckets.values().stream()
                .sorted(Comparator.comparing((TeamFinanceBucket bucket) -> bucket.incomeAmount).reversed())
                .limit(6)
                .map(bucket -> new FinanceTeamStatResponse(
                        bucket.teamLabel,
                        (long) bucket.memberIds.size(),
                        bucket.serviceCount,
                        scale(bucket.orderIncome),
                        scale(bucket.incomeAmount)))
                .toList();
    }

    private enum MonthlyMetric {
        ORDER,
        EMPLOYEE,
        DISTRIBUTOR,
        WITHDRAW
    }

    private static class MonthlyFinanceBucket {
        private BigDecimal orderIncome = BigDecimal.ZERO;
        private BigDecimal employeeIncome = BigDecimal.ZERO;
        private BigDecimal distributorIncome = BigDecimal.ZERO;
        private BigDecimal withdrawAmount = BigDecimal.ZERO;
    }

    private static class TeamFinanceBucket {
        private final String teamLabel;
        private final Map<Long, Boolean> memberIds = new LinkedHashMap<>();
        private long serviceCount;
        private BigDecimal orderIncome = BigDecimal.ZERO;
        private BigDecimal incomeAmount = BigDecimal.ZERO;

        private TeamFinanceBucket(String teamLabel) {
            this.teamLabel = teamLabel;
        }
    }

    private record BindingQr(String contactWayId,
                             String qrCodeUrl,
                             String shortLink,
                             String status,
                             String message) {
    }
}
