package com.seedcrm.crm.permission.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import com.seedcrm.crm.wecom.entity.CustomerWecomRelation;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.workbench.service.StaffDirectoryService;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CustomerPermissionGuard {

    private final PermissionService permissionService;
    private final AuthService authService;
    private final CustomerMapper customerMapper;
    private final CustomerWecomRelationMapper customerWecomRelationMapper;
    private final OrderMapper orderMapper;
    private final OrderPermissionGuard orderPermissionGuard;
    private final OrderPermissionResourceResolver resourceResolver;
    private final StaffDirectoryService staffDirectoryService;

    public CustomerPermissionGuard(PermissionService permissionService,
                                   AuthService authService,
                                   CustomerMapper customerMapper,
                                   CustomerWecomRelationMapper customerWecomRelationMapper,
                                   OrderMapper orderMapper,
                                   OrderPermissionGuard orderPermissionGuard,
                                   OrderPermissionResourceResolver resourceResolver,
                                   StaffDirectoryService staffDirectoryService) {
        this.permissionService = permissionService;
        this.authService = authService;
        this.customerMapper = customerMapper;
        this.customerWecomRelationMapper = customerWecomRelationMapper;
        this.orderMapper = orderMapper;
        this.orderPermissionGuard = orderPermissionGuard;
        this.resourceResolver = resourceResolver;
        this.staffDirectoryService = staffDirectoryService;
    }

    public void checkView(PermissionRequestContext context, Long customerId) {
        PermissionCheckResponse response = checkViewPermission(context, customerId);
        if (!response.isAllowed()) {
            throw new BusinessException("customer view denied: " + response.getReason());
        }
    }

    public boolean canView(PermissionRequestContext context, Long customerId) {
        return checkViewPermission(context, customerId).isAllowed();
    }

    private PermissionCheckResponse checkViewPermission(PermissionRequestContext context, Long customerId) {
        Customer customer = getCustomerOrThrow(customerId);
        if (context == null || !StringUtils.hasText(context.getRoleCode())) {
            return denied(null, "missing permission context");
        }

        if ("PRIVATE_DOMAIN_SERVICE".equals(normalizeUpper(context.getRoleCode()))) {
            Long boundOwnerId = isBoundToPrivateDomainUser(customer.getId(), context)
                    ? context.getCurrentUserId()
                    : null;
            return permissionService.check(buildRequest(context, boundOwnerId));
        }

        List<Order> orders = loadCustomerOrders(customer.getId());
        if ("STORE".equalsIgnoreCase(context.getDataScope())) {
            return checkViaRelatedOrders(context, orders, true);
        }
        if ("ALL".equalsIgnoreCase(context.getDataScope())) {
            return permissionService.check(buildRequest(context, null));
        }
        return checkViaRelatedOrders(context, orders, false);
    }

    private PermissionCheckResponse checkViaRelatedOrders(PermissionRequestContext context,
                                                          List<Order> orders,
                                                          boolean storeScope) {
        PermissionCheckResponse lastDenied = denied(context, "no accessible customer order");
        for (Order order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }
            try {
                Long resourceOwnerId = storeScope
                        ? resourceResolver.resolveOrderStoreScopeOwnerId(order.getId())
                        : resourceResolver.resolveOrderOwnerId(order.getId());
                PermissionCheckResponse response = permissionService.check(buildRequest(context, resourceOwnerId));
                if (!response.isAllowed()) {
                    lastDenied = response;
                    continue;
                }
                if (orderPermissionGuard.canView(context, order.getId())) {
                    return response;
                }
                lastDenied = denied(context, "related order scope rejected");
            } catch (BusinessException exception) {
                lastDenied = denied(context, exception.getMessage());
            }
        }
        return lastDenied;
    }

    private PermissionCheckRequest buildRequest(PermissionRequestContext context, Long resourceOwnerId) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("CUSTOMER");
        request.setActionCode("VIEW");
        request.setRoleCode(context.getRoleCode());
        request.setDataScope(context.getDataScope());
        request.setCurrentUserId(context.getCurrentUserId());
        request.setCurrentStoreId(context.getCurrentStoreId());
        request.setResourceStoreId(authService.resolveStoreId(resourceOwnerId));
        request.setTeamMemberIds(context.getTeamMemberIds());
        request.setBoundCustomerUserId(resourceOwnerId);
        request.setResourceOwnerId(resourceOwnerId);
        return request;
    }

    private Customer getCustomerOrThrow(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new BusinessException("customerId is required");
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("customer not found");
        }
        return customer;
    }

    private List<Order> loadCustomerOrders(Long customerId) {
        return orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getCustomerId, customerId)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId));
    }

    private boolean isBoundToPrivateDomainUser(Long customerId, PermissionRequestContext context) {
        if (context.getCurrentUserId() == null) {
            return false;
        }
        CustomerWecomRelation relation = customerWecomRelationMapper.selectOne(Wrappers.<CustomerWecomRelation>lambdaQuery()
                .eq(CustomerWecomRelation::getCustomerId, customerId)
                .last("LIMIT 1"));
        if (relation == null || !StringUtils.hasText(relation.getExternalUserid())) {
            return false;
        }
        String wecomUserId = normalize(relation.getWecomUserId());
        String numericUserId = normalize(String.valueOf(context.getCurrentUserId()));
        String wecomAccount = normalize(staffDirectoryService.getWecomAccount(context.getCurrentUserId()));
        return numericUserId.equals(wecomUserId) || wecomAccount.equals(wecomUserId);
    }

    private PermissionCheckResponse denied(PermissionRequestContext context, String reason) {
        return new PermissionCheckResponse(false, null, context == null ? null : context.getDataScope(), reason);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
