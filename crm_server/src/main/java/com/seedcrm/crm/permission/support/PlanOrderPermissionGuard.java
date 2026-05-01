package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class PlanOrderPermissionGuard {

    private static final String FINANCE_ROLE_CODE = "FINANCE";

    private final PermissionService permissionService;
    private final AuthService authService;
    private final OrderPermissionResourceResolver resourceResolver;

    public PlanOrderPermissionGuard(PermissionService permissionService,
                                    AuthService authService,
                                    OrderPermissionResourceResolver resourceResolver) {
        this.permissionService = permissionService;
        this.authService = authService;
        this.resourceResolver = resourceResolver;
    }

    public void checkCreate(PermissionRequestContext context, Long orderId) {
        rejectFinanceBusinessMutation(context, "plan order create denied");
        PermissionCheckRequest request = buildCheckRequest(context, "PLANORDER", "CREATE",
                resolveOrderResourceOwnerId(context, orderId));
        assertAllowed(permissionService.check(request), "plan order create denied");
    }

    public void checkUpdate(PermissionRequestContext context, Long planOrderId) {
        rejectFinanceBusinessMutation(context, "plan order update denied");
        PermissionCheckRequest request = buildCheckRequest(context, "PLANORDER", "UPDATE",
                resolvePlanOrderResourceOwnerId(context, planOrderId));
        assertAllowed(permissionService.check(request), "plan order update denied");
    }

    public void checkView(PermissionRequestContext context, Long planOrderId) {
        assertAllowed(checkViewPermission(context, planOrderId), "plan order view denied");
    }

    public void checkAssignRole(PermissionRequestContext context, Long planOrderId) {
        rejectFinanceBusinessMutation(context, "plan order assign role denied");
        PermissionCheckRequest request = buildCheckRequest(context, "PLANORDER", "ASSIGN_ROLE",
                resolvePlanOrderResourceOwnerId(context, planOrderId));
        assertAllowed(permissionService.check(request), "plan order assign role denied");
    }

    public boolean canView(PermissionRequestContext context, Long planOrderId) {
        return checkViewPermission(context, planOrderId).isAllowed();
    }

    private PermissionCheckRequest buildCheckRequest(PermissionRequestContext context,
                                                     String moduleCode,
                                                     String actionCode,
                                                     Long resourceOwnerId) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode(moduleCode);
        request.setActionCode(actionCode);
        request.setRoleCode(context.getRoleCode());
        request.setDataScope(context.getDataScope());
        request.setCurrentUserId(context.getCurrentUserId());
        request.setCurrentStoreId(context.getCurrentStoreId());
        request.setResourceStoreId(authService.resolveStoreId(resourceOwnerId));
        request.setTeamMemberIds(context.getTeamMemberIds());
        request.setBoundCustomerUserId(context.getBoundCustomerUserId());
        request.setResourceOwnerId(resourceOwnerId);
        return request;
    }

    private void assertAllowed(PermissionCheckResponse response, String messagePrefix) {
        if (!response.isAllowed()) {
            throw new BusinessException(messagePrefix + ": " + response.getReason());
        }
    }

    private PermissionCheckResponse checkViewPermission(PermissionRequestContext context, Long planOrderId) {
        PermissionCheckRequest request = buildCheckRequest(context, "PLANORDER", "VIEW",
                resolvePlanOrderResourceOwnerId(context, planOrderId));
        return permissionService.check(request);
    }

    private void rejectFinanceBusinessMutation(PermissionRequestContext context, String messagePrefix) {
        if (context != null
                && context.getRoleCode() != null
                && FINANCE_ROLE_CODE.equalsIgnoreCase(context.getRoleCode().trim())) {
            throw new BusinessException(messagePrefix + ": finance role is ledger-only and cannot mutate service flow");
        }
    }

    private Long resolveOrderResourceOwnerId(PermissionRequestContext context, Long orderId) {
        if (context != null && "STORE".equalsIgnoreCase(context.getDataScope())) {
            return resourceResolver.resolveOrderStoreScopeOwnerId(orderId);
        }
        return resourceResolver.resolveOrderOwnerId(orderId);
    }

    private Long resolvePlanOrderResourceOwnerId(PermissionRequestContext context, Long planOrderId) {
        if (context != null && "STORE".equalsIgnoreCase(context.getDataScope())) {
            return resourceResolver.resolvePlanOrderStoreScopeOwnerId(planOrderId);
        }
        return resourceResolver.resolvePlanOrderOwnerId(planOrderId);
    }
}
