package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class WecomModuleGuard {

    private final PermissionService permissionService;

    public WecomModuleGuard(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void checkView(PermissionRequestContext context) {
        assertAllowed(check(context, "VIEW"), "wecom view denied");
    }

    public void checkUpdate(PermissionRequestContext context) {
        assertAllowed(check(context, "UPDATE"), "wecom update denied");
    }

    public void checkConfigManage(PermissionRequestContext context) {
        if (!"ADMIN".equalsIgnoreCase(String.valueOf(context.getRoleCode()).trim())) {
            throw new BusinessException("wecom config manage denied: admin required");
        }
        assertAllowed(check(context, "UPDATE"), "wecom update denied");
    }

    private PermissionCheckResponse check(PermissionRequestContext context, String actionCode) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("WECOM");
        request.setActionCode(actionCode);
        request.setRoleCode(context.getRoleCode());
        // WECOM console endpoints are workspace-level actions. Use the module policy
        // directly instead of filtering them by the user's default data scope.
        request.setDataScope(null);
        request.setCurrentUserId(context.getCurrentUserId());
        request.setCurrentStoreId(context.getCurrentStoreId());
        request.setResourceStoreId(context.getCurrentStoreId());
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
