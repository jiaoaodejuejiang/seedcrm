package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class FinancePermissionGuard {

    private final PermissionService permissionService;

    public FinancePermissionGuard(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void checkView(PermissionRequestContext context) {
        assertAllowed(check(context, "VIEW"), "finance view denied");
    }

    public void checkUpdate(PermissionRequestContext context) {
        assertAllowed(check(context, "UPDATE"), "finance update denied");
    }

    private PermissionCheckResponse check(PermissionRequestContext context, String actionCode) {
        if (context == null || context.getRoleCode() == null) {
            throw new BusinessException("finance permission context is required");
        }
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("FINANCE");
        request.setActionCode(actionCode);
        request.setRoleCode(context.getRoleCode());
        request.setDataScope(null);
        request.setCurrentUserId(context.getCurrentUserId());
        request.setCurrentStoreId(context.getCurrentStoreId());
        request.setResourceStoreId(context.getCurrentStoreId());
        request.setTeamMemberIds(context.getTeamMemberIds());
        request.setBoundCustomerUserId(null);
        request.setResourceOwnerId(context.getCurrentUserId());
        return permissionService.check(request);
    }

    private void assertAllowed(PermissionCheckResponse response, String messagePrefix) {
        if (!response.isAllowed()) {
            throw new BusinessException(messagePrefix + ": " + response.getReason());
        }
    }
}
