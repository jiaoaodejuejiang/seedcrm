package com.seedcrm.crm.customer.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.dto.MemberResponses.MemberListItemResponse;
import com.seedcrm.crm.customer.dto.MemberResponses.MemberListResponse;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.customer.service.MemberService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.SensitiveDataProjectionService;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.wecom.entity.CustomerWecomRelation;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.workbench.service.StaffDirectoryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MemberServiceImpl implements MemberService {

    private static final Set<String> MEMBER_ROLES = Set.of("ADMIN", "PRIVATE_DOMAIN_SERVICE");

    private final CustomerMapper customerMapper;
    private final OrderMapper orderMapper;
    private final PlanOrderMapper planOrderMapper;
    private final CustomerWecomRelationMapper customerWecomRelationMapper;
    private final StaffDirectoryService staffDirectoryService;
    private final SensitiveDataProjectionService sensitiveDataProjectionService;

    public MemberServiceImpl(CustomerMapper customerMapper,
                             OrderMapper orderMapper,
                             PlanOrderMapper planOrderMapper,
                             CustomerWecomRelationMapper customerWecomRelationMapper,
                             StaffDirectoryService staffDirectoryService,
                             SensitiveDataProjectionService sensitiveDataProjectionService) {
        this.customerMapper = customerMapper;
        this.orderMapper = orderMapper;
        this.planOrderMapper = planOrderMapper;
        this.customerWecomRelationMapper = customerWecomRelationMapper;
        this.staffDirectoryService = staffDirectoryService;
        this.sensitiveDataProjectionService = sensitiveDataProjectionService;
    }

    @Override
    public MemberListResponse listMembers(String sourceTab,
                                          String phone,
                                          String name,
                                          String externalMemberId,
                                          Integer page,
                                          Integer pageSize,
                                          PermissionRequestContext context) {
        ensurePermission(context);
        int currentPage = page == null || page < 1 ? 1 : page;
        int currentPageSize = pageSize == null ? 30 : Math.max(1, Math.min(pageSize, 50));
        String normalizedTab = normalize(sourceTab);
        String normalizedPhone = normalize(phone);
        String normalizedName = normalize(name);
        String normalizedExternalMemberId = normalize(externalMemberId);

        List<Customer> customers = customerMapper.selectList(Wrappers.<Customer>lambdaQuery()
                .orderByDesc(Customer::getLastOrderTime)
                .orderByDesc(Customer::getUpdateTime)
                .orderByDesc(Customer::getId));

        List<Customer> filteredCustomers = customers.stream()
                .filter(customer -> matchesSourceTab(customer, normalizedTab))
                .filter(customer -> !StringUtils.hasText(normalizedPhone)
                        || normalize(customer.getPhone()).contains(normalizedPhone))
                .filter(customer -> !StringUtils.hasText(normalizedName)
                        || normalize(customer.getName()).contains(normalizedName))
                .filter(customer -> !StringUtils.hasText(normalizedExternalMemberId)
                        || normalize(customer.getExternalMemberId()).contains(normalizedExternalMemberId))
                .toList();

        Map<Long, List<Order>> ordersByCustomerId = loadOrdersByCustomer(filteredCustomers);
        Map<Long, CustomerWecomRelation> allWecomMap = loadWecomMap(filteredCustomers);
        List<Customer> paidMembers = filteredCustomers.stream()
                .filter(customer -> !ordersByCustomerId.getOrDefault(customer.getId(), List.of()).isEmpty())
                .filter(customer -> canViewMember(customer, allWecomMap.get(customer.getId()), context))
                .toList();
        long total = paidMembers.size();
        List<Customer> pageRows = paidMembers.stream()
                .skip((long) (currentPage - 1) * currentPageSize)
                .limit(currentPageSize)
                .toList();
        Map<Long, PlanOrder> planOrdersByOrderId = loadPlanOrdersByOrder(pageRows, ordersByCustomerId);
        List<MemberListItemResponse> records = pageRows.stream()
                .map(customer -> buildItem(customer,
                        ordersByCustomerId.getOrDefault(customer.getId(), List.of()),
                        allWecomMap.get(customer.getId()),
                        planOrdersByOrderId,
                        context))
                .toList();
        return new MemberListResponse(records, total, currentPage, currentPageSize);
    }

    private void ensurePermission(PermissionRequestContext context) {
        String roleCode = normalizeUpper(context == null ? null : context.getRoleCode());
        if (!MEMBER_ROLES.contains(roleCode)) {
            throw new BusinessException("member view denied: role not allowed");
        }
    }

    private boolean canViewMember(Customer customer,
                                  CustomerWecomRelation wecomRelation,
                                  PermissionRequestContext context) {
        String roleCode = normalizeUpper(context == null ? null : context.getRoleCode());
        if ("ADMIN".equals(roleCode)) {
            return true;
        }
        if (!"PRIVATE_DOMAIN_SERVICE".equals(roleCode)) {
            return false;
        }
        if (wecomRelation == null || !StringUtils.hasText(wecomRelation.getExternalUserid())) {
            return false;
        }
        Long currentUserId = context.getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }
        String wecomUserId = normalize(wecomRelation.getWecomUserId());
        String numericUserId = normalize(String.valueOf(currentUserId));
        String wecomAccount = normalize(staffDirectoryService.getWecomAccount(currentUserId));
        return numericUserId.equals(wecomUserId) || wecomAccount.equals(wecomUserId);
    }

    private Map<Long, List<Order>> loadOrdersByCustomer(List<Customer> customers) {
        List<Long> customerIds = customers.stream()
                .map(Customer::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                        .in(Order::getCustomerId, customerIds)
                        .orderByDesc(Order::getCreateTime)
                        .orderByDesc(Order::getId))
                .stream()
                .filter(order -> order.getCustomerId() != null)
                .collect(Collectors.groupingBy(Order::getCustomerId));
    }

    private Map<Long, CustomerWecomRelation> loadWecomMap(List<Customer> customers) {
        List<Long> customerIds = customers.stream()
                .map(Customer::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return customerWecomRelationMapper.selectList(Wrappers.<CustomerWecomRelation>lambdaQuery()
                        .in(CustomerWecomRelation::getCustomerId, customerIds))
                .stream()
                .filter(relation -> relation.getCustomerId() != null)
                .collect(Collectors.toMap(CustomerWecomRelation::getCustomerId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, PlanOrder> loadPlanOrdersByOrder(List<Customer> customers,
                                                       Map<Long, List<Order>> ordersByCustomerId) {
        List<Long> orderIds = customers.stream()
                .flatMap(customer -> ordersByCustomerId.getOrDefault(customer.getId(), List.of()).stream())
                .map(Order::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return planOrderMapper.selectList(Wrappers.<PlanOrder>lambdaQuery()
                        .in(PlanOrder::getOrderId, orderIds)
                        .orderByDesc(PlanOrder::getCreateTime)
                        .orderByDesc(PlanOrder::getId))
                .stream()
                .filter(planOrder -> planOrder.getOrderId() != null)
                .collect(Collectors.toMap(PlanOrder::getOrderId, Function.identity(), (left, right) -> left));
    }

    private MemberListItemResponse buildItem(Customer customer,
                                             List<Order> orders,
                                             CustomerWecomRelation wecomRelation,
                                             Map<Long, PlanOrder> planOrdersByOrderId,
                                             PermissionRequestContext context) {
        Order latestOrder = latestOrder(orders);
        PlanOrder latestPlanOrder = latestOrder == null ? null : planOrdersByOrderId.get(latestOrder.getId());
        BigDecimal totalAmount = orders.stream()
                .map(Order::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        MemberListItemResponse response = new MemberListItemResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getSource(),
                customer.getSourceChannel(),
                sourceDisplayName(customer),
                customer.getExternalPartnerCode(),
                customer.getExternalMemberId(),
                displayMemberRole(customer.getExternalMemberRole()),
                wecomRelation != null && StringUtils.hasText(wecomRelation.getExternalUserid()),
                privateDomainOwner(wecomRelation),
                customer.getTag(),
                latestOrder == null ? null : latestOrder.getId(),
                latestOrder == null ? null : latestOrder.getOrderNo(),
                latestOrder == null ? null : OrderStatus.toApiValue(latestOrder.getStatus()),
                latestOrder == null ? null : latestOrder.getAmount(),
                latestOrder == null ? null : latestOrder.getCreateTime(),
                latestPlanOrder == null ? null : latestPlanOrder.getId(),
                latestPlanOrder == null ? null : latestPlanOrder.getStatus(),
                latestPlanOrder == null ? null : planOrderTime(latestPlanOrder),
                (long) orders.size(),
                totalAmount,
                customer.getUpdateTime(),
                customer.getCreateTime());
        return sensitiveDataProjectionService.projectMemberListItem(response, context);
    }

    private Order latestOrder(List<Order> orders) {
        return orders.stream()
                .max(Comparator.comparing(Order::getCreateTime, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(Order::getId, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
    }

    private String privateDomainOwner(CustomerWecomRelation wecomRelation) {
        if (wecomRelation == null || !StringUtils.hasText(wecomRelation.getWecomUserId())) {
            return null;
        }
        return wecomRelation.getWecomUserId().trim();
    }

    private LocalDateTime planOrderTime(PlanOrder planOrder) {
        if (planOrder.getFinishTime() != null) {
            return planOrder.getFinishTime();
        }
        if (planOrder.getStartTime() != null) {
            return planOrder.getStartTime();
        }
        if (planOrder.getArriveTime() != null) {
            return planOrder.getArriveTime();
        }
        return planOrder.getCreateTime();
    }

    private boolean matchesSourceTab(Customer customer, String normalizedTab) {
        if (!StringUtils.hasText(normalizedTab) || "all".equals(normalizedTab)) {
            return true;
        }
        if ("distribution".equals(normalizedTab)) {
            return isDistribution(customer);
        }
        if ("douyin".equals(normalizedTab)) {
            return isDouyin(customer);
        }
        return normalizedTab.equals(normalize(customer.getSource()))
                || normalizedTab.equals(normalize(customer.getSourceChannel()));
    }

    private String sourceDisplayName(Customer customer) {
        if (isDistribution(customer)) {
            return "分销成交";
        }
        if (isDouyin(customer)) {
            return "抖音成交";
        }
        return StringUtils.hasText(customer.getSourceChannel()) ? customer.getSourceChannel() : customer.getSource();
    }

    private boolean isDistribution(Customer customer) {
        return "distribution".equals(normalize(customer == null ? null : customer.getSource()))
                || "distributor".equals(normalize(customer == null ? null : customer.getSourceChannel()))
                || "distribution".equals(normalize(customer == null ? null : customer.getSourceChannel()));
    }

    private boolean isDouyin(Customer customer) {
        return "douyin".equals(normalize(customer == null ? null : customer.getSource()))
                || "douyin".equals(normalize(customer == null ? null : customer.getSourceChannel()));
    }

    private String displayMemberRole(String role) {
        String normalized = normalize(role);
        if ("leader".equals(normalized)) {
            return "团长";
        }
        if ("member".equals(normalized)) {
            return "团员";
        }
        if ("buyer".equals(normalized)) {
            return "会员";
        }
        return StringUtils.hasText(role) ? role.trim() : "会员";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
