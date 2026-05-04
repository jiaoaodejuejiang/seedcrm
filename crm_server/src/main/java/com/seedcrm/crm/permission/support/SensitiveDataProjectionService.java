package com.seedcrm.crm.permission.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.customer.dto.MemberResponses.MemberListItemResponse;
import com.seedcrm.crm.order.dto.OrderResponse;
import com.seedcrm.crm.order.support.OrderAmountMaskingSupport;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CustomerProfileResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.OrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderWorkbenchResponse;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataProjectionService {

    private static final String CONFIG_STORE_AMOUNT_HIDDEN = "amount.visibility.store_staff_hidden";
    private static final String CONFIG_STORE_AMOUNT_HIDDEN_ROLES = "amount.visibility.store_staff_hidden_roles";
    private static final String CONFIG_SERVICE_AMOUNT_HIDDEN_ROLES = "amount.visibility.service_confirm_hidden_roles";
    private static final String CONFIG_SERVICE_AMOUNT_EDIT_ROLES = "amount.visibility.service_confirm_edit_roles";
    private static final String DEFAULT_STORE_AMOUNT_HIDDEN_ROLES =
            "STORE_SERVICE,STORE_MANAGER,PHOTOGRAPHER,MAKEUP_ARTIST,PHOTO_SELECTOR";
    private static final String DEFAULT_SERVICE_AMOUNT_HIDDEN_ROLES =
            "STORE_SERVICE,STORE_MANAGER,PHOTOGRAPHER,MAKEUP_ARTIST,PHOTO_SELECTOR";
    private static final String DEFAULT_SERVICE_AMOUNT_EDIT_ROLES =
            "ADMIN,FINANCE";
    private static final Set<String> STORE_AMOUNT_RESTRICTED_ROLES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");
    private static final Set<String> DEFAULT_SERVICE_AMOUNT_RESTRICTED_ROLES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");
    private static final Set<String> DEFAULT_SERVICE_AMOUNT_EDITOR_ROLES = Set.of(
            "ADMIN",
            "FINANCE");
    private static final Set<String> MEMBER_AMOUNT_VIEW_ROLES = Set.of("ADMIN", "FINANCE");

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;

    public SensitiveDataProjectionService(ObjectMapper objectMapper,
                                          SystemConfigService systemConfigService) {
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
    }

    public OrderResponse projectOrderResponse(OrderResponse response, PermissionRequestContext context) {
        if (response == null) {
            return null;
        }
        if (shouldMaskBusinessAmounts(context)) {
            response.maskBusinessAmounts();
        }
        if (shouldMaskServiceAmounts(context)) {
            response.maskServiceAmounts(objectMapper);
        }
        return response;
    }

    public OrderItemResponse projectOrderItem(OrderItemResponse response, PermissionRequestContext context) {
        if (response == null) {
            return null;
        }
        if (shouldMaskBusinessAmounts(context)) {
            response.setAmount(null);
            response.setDeposit(null);
            response.setVerificationCode(null);
        }
        if (shouldMaskServiceAmounts(context)) {
            response.setServiceDetailJson(OrderAmountMaskingSupport.maskServiceDetailJson(
                    response.getServiceDetailJson(), objectMapper));
        }
        return response;
    }

    public PlanOrderItemResponse projectPlanOrderItem(PlanOrderItemResponse response, PermissionRequestContext context) {
        if (response != null && shouldMaskBusinessAmounts(context)) {
            response.setAmount(null);
        }
        return response;
    }

    public PlanOrderWorkbenchResponse projectPlanOrderWorkbench(PlanOrderWorkbenchResponse response,
                                                                PermissionRequestContext context) {
        if (response == null) {
            return null;
        }
        projectPlanOrderItem(response.getSummary(), context);
        projectOrderItem(response.getOrder(), context);
        return response;
    }

    public CustomerProfileResponse projectCustomerProfile(CustomerProfileResponse response,
                                                          PermissionRequestContext context) {
        if (response == null || response.getOrderHistory() == null) {
            return response;
        }
        response.getOrderHistory().forEach(order -> projectOrderItem(order, context));
        return response;
    }

    public MemberListItemResponse projectMemberListItem(MemberListItemResponse response,
                                                        PermissionRequestContext context) {
        if (response == null || canViewMemberAmounts(context)) {
            return response;
        }
        response.setLatestOrderAmount(null);
        response.setTotalOrderAmount(null);
        return response;
    }

    public boolean shouldMaskBusinessAmounts(PermissionRequestContext context) {
        String roleCode = normalizeRole(context);
        return roleCode != null
                && systemConfigService.getBoolean(CONFIG_STORE_AMOUNT_HIDDEN, true)
                && configuredRoles(CONFIG_STORE_AMOUNT_HIDDEN_ROLES,
                DEFAULT_STORE_AMOUNT_HIDDEN_ROLES,
                STORE_AMOUNT_RESTRICTED_ROLES).contains(roleCode);
    }

    public boolean shouldMaskServiceAmounts(PermissionRequestContext context) {
        String roleCode = normalizeRole(context);
        return roleCode != null
                && systemConfigService.getBoolean(CONFIG_STORE_AMOUNT_HIDDEN, true)
                && configuredRoles(CONFIG_SERVICE_AMOUNT_HIDDEN_ROLES,
                DEFAULT_SERVICE_AMOUNT_HIDDEN_ROLES,
                DEFAULT_SERVICE_AMOUNT_RESTRICTED_ROLES).contains(roleCode);
    }

    public boolean canEditServiceAmounts(PermissionRequestContext context) {
        String roleCode = normalizeRole(context);
        return roleCode != null
                && configuredRoles(CONFIG_SERVICE_AMOUNT_EDIT_ROLES,
                DEFAULT_SERVICE_AMOUNT_EDIT_ROLES,
                DEFAULT_SERVICE_AMOUNT_EDITOR_ROLES).contains(roleCode);
    }

    private boolean canViewMemberAmounts(PermissionRequestContext context) {
        String roleCode = normalizeRole(context);
        return roleCode != null && MEMBER_AMOUNT_VIEW_ROLES.contains(roleCode);
    }

    private Set<String> configuredRoles(String key, String defaultRoles, Set<String> fallbackRoles) {
        String configuredRoles = systemConfigService.getString(key, defaultRoles);
        Set<String> roles = Arrays.stream(configuredRoles.split("[,\\uFF0C\\s]+"))
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isBlank())
                .collect(Collectors.toSet());
        return roles.isEmpty() ? fallbackRoles : roles;
    }

    private String normalizeRole(PermissionRequestContext context) {
        if (context == null || context.getRoleCode() == null || context.getRoleCode().isBlank()) {
            return null;
        }
        return context.getRoleCode().trim().toUpperCase(Locale.ROOT);
    }
}
