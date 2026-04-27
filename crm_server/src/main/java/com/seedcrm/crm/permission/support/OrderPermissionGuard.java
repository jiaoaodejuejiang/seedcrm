package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class OrderPermissionGuard {

    private final PermissionService permissionService;
    private final AuthService authService;
    private final OrderPermissionResourceResolver resourceResolver;

    public OrderPermissionGuard(PermissionService permissionService,
                                AuthService authService,
                                OrderPermissionResourceResolver resourceResolver) {
        this.permissionService = permissionService;
        this.authService = authService;
        this.resourceResolver = resourceResolver;
    }

    public void checkCreate(PermissionRequestContext context, OrderCreateDTO request) {
        Long clueOwnerId = resourceResolver.resolveClueOwnerId(request == null ? null : request.getClueId());
        PermissionCheckRequest checkRequest = buildCheckRequest(context, "ORDER", "UPDATE", clueOwnerId);
        assertAllowed(permissionService.check(checkRequest), "order create denied");
    }

    public void checkUpdate(PermissionRequestContext context, Long orderId) {
        PermissionCheckRequest checkRequest = buildCheckRequest(context, "ORDER", "UPDATE",
                resolveResourceOwnerId(context, orderId));
        assertAllowed(permissionService.check(checkRequest), "order update denied");
    }

    public void checkRefund(PermissionRequestContext context, Long orderId, String refundScene) {
        PermissionCheckRequest checkRequest = buildCheckRequest(context, "ORDER", resolveRefundActionCode(refundScene),
                resolveResourceOwnerId(context, orderId));
        assertAllowed(permissionService.check(checkRequest), "order refund denied");
    }

    public void checkView(PermissionRequestContext context, Long orderId) {
        assertAllowed(checkViewPermission(context, orderId), "order view denied");
    }

    public boolean canView(PermissionRequestContext context, Long orderId) {
        return checkViewPermission(context, orderId).isAllowed();
    }

    public void checkFinish(PermissionRequestContext context, Long orderId) {
        PermissionCheckRequest checkRequest = buildCheckRequest(context, "ORDER", "FINISH",
                resolveResourceOwnerId(context, orderId));
        assertAllowed(permissionService.check(checkRequest), "order finish denied");
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

    private PermissionCheckResponse checkViewPermission(PermissionRequestContext context, Long orderId) {
        PermissionCheckRequest checkRequest = buildCheckRequest(context, "ORDER", "VIEW",
                resolveResourceOwnerId(context, orderId));
        return permissionService.check(checkRequest);
    }

    private String resolveRefundActionCode(String refundScene) {
        String normalizedScene = refundScene == null ? "" : refundScene.trim().toUpperCase();
        return "FINANCE_VERIFIED_PAYMENT".equals(normalizedScene) ? "REFUND_PAYMENT" : "REFUND_STORE";
    }

    private Long resolveResourceOwnerId(PermissionRequestContext context, Long orderId) {
        if (context != null && "STORE".equalsIgnoreCase(context.getDataScope())) {
            return resourceResolver.resolveOrderStoreScopeOwnerId(orderId);
        }
        return resourceResolver.resolveOrderOwnerId(orderId);
    }
}
