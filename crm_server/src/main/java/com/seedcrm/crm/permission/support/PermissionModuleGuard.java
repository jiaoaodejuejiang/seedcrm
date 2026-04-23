package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class PermissionModuleGuard {

    private final PermissionService permissionService;

    public PermissionModuleGuard(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void checkView(PermissionRequestContext context) {
        assertAllowed(check(context, "VIEW"), "permission view denied");
    }

    public void checkUpdate(PermissionRequestContext context) {
        assertAllowed(check(context, "UPDATE"), "permission update denied");
    }

    public void checkCheck(PermissionRequestContext context) {
        assertAllowed(check(context, "CHECK"), "permission check denied");
    }

    private PermissionCheckResponse check(PermissionRequestContext context, String actionCode) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("PERMISSION");
        request.setActionCode(actionCode);
        request.setRoleCode(context.getRoleCode());
        request.setDataScope(context.getDataScope());
        request.setCurrentUserId(context.getCurrentUserId());
        request.setCurrentStoreId(context.getCurrentStoreId());
        request.setResourceStoreId(context.getResourceStoreId());
        request.setTeamMemberIds(context.getTeamMemberIds());
        request.setBoundCustomerUserId(context.getBoundCustomerUserId());
        return permissionService.check(request);
    }

    private void assertAllowed(PermissionCheckResponse response, String messagePrefix) {
        if (!response.isAllowed()) {
            throw new BusinessException(messagePrefix + ": " + response.getReason());
        }
    }
}
