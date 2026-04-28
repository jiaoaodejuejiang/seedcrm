package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class SchedulerModuleGuard {

    private final PermissionService permissionService;

    public SchedulerModuleGuard(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void checkView(PermissionRequestContext context) {
        assertAllowed(check(context, "VIEW"), "scheduler view denied");
    }

    public void checkUpdate(PermissionRequestContext context) {
        assertAllowed(check(context, "UPDATE"), "scheduler update denied");
    }

    public void checkTrigger(PermissionRequestContext context) {
        assertAllowed(check(context, "TRIGGER"), "scheduler trigger denied");
    }

    public void checkDebug(PermissionRequestContext context) {
        assertAllowed(check(context, "DEBUG"), "scheduler debug denied");
    }

    private PermissionCheckResponse check(PermissionRequestContext context, String actionCode) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("SCHEDULER");
        request.setActionCode(actionCode);
        request.setRoleCode(context.getRoleCode());
        request.setDataScope(context.getDataScope());
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
