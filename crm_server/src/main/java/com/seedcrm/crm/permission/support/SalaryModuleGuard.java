package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class SalaryModuleGuard {

    private final PermissionService permissionService;

    public SalaryModuleGuard(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void checkView(PermissionRequestContext context, Long salaryUserId) {
        assertAllowed(check(context, "VIEW", salaryUserId), "salary view denied");
    }

    public void checkUpdate(PermissionRequestContext context) {
        assertAllowed(check(context, "UPDATE", context == null ? null : context.getCurrentUserId()), "salary update denied");
    }

    private PermissionCheckResponse check(PermissionRequestContext context, String actionCode, Long resourceOwnerId) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("SALARY");
        request.setActionCode(actionCode);
        request.setRoleCode(context.getRoleCode());
        // Salary uses its own permission policy scope. This keeps a role's CLUE/ORDER
        // data scope from accidentally expanding salary visibility.
        request.setDataScope(null);
        request.setCurrentUserId(context.getCurrentUserId());
        request.setCurrentStoreId(context.getCurrentStoreId());
        request.setResourceStoreId(context.getCurrentStoreId());
        request.setTeamMemberIds(context.getTeamMemberIds());
        // Salary "SELF" means the salary owner only; WeCom customer binding must
        // not expand compensation visibility.
        request.setBoundCustomerUserId(null);
        request.setResourceOwnerId(resourceOwnerId);
        return permissionService.check(request);
    }

    private void assertAllowed(PermissionCheckResponse response, String messagePrefix) {
        if (!response.isAllowed()) {
            throw new BusinessException(messagePrefix + ": " + response.getReason());
        }
    }
}
