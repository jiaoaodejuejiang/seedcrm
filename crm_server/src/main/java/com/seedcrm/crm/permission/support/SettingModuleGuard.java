package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class SettingModuleGuard {

    private final PermissionService permissionService;

    public SettingModuleGuard(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void checkView(PermissionRequestContext context) {
        assertAllowed(check(context, "VIEW"), "setting view denied");
    }

    public void checkUpdate(PermissionRequestContext context) {
        assertAllowed(check(context, "UPDATE"), "setting update denied");
    }

    public void checkDebug(PermissionRequestContext context) {
        assertAllowed(check(context, "DEBUG"), "setting debug denied");
    }

    public void checkConfigAudit(PermissionRequestContext context) {
        assertAllowed(check(context, "CONFIG_AUDIT"), "system config audit denied");
    }

    public void checkConfigDraft(PermissionRequestContext context) {
        assertAllowed(check(context, "CONFIG_DRAFT"), "system config draft denied");
    }

    public void checkConfigPublish(PermissionRequestContext context) {
        assertAllowed(check(context, "CONFIG_PUBLISH"), "system config publish denied");
    }

    public void checkConfigRollback(PermissionRequestContext context) {
        assertAllowed(check(context, "CONFIG_ROLLBACK"), "system config rollback denied");
    }

    public void checkSystemFlowView(PermissionRequestContext context) {
        assertAllowed(check(context, "SYSTEM_FLOW_VIEW"), "system flow view denied");
    }

    public void checkSystemFlowDraft(PermissionRequestContext context) {
        assertAllowed(check(context, "SYSTEM_FLOW_DRAFT"), "system flow draft denied");
    }

    public void checkSystemFlowPublish(PermissionRequestContext context) {
        assertAllowed(check(context, "SYSTEM_FLOW_PUBLISH"), "system flow publish denied");
    }

    public void checkSystemFlowDebug(PermissionRequestContext context) {
        assertAllowed(check(context, "SYSTEM_FLOW_DEBUG"), "system flow debug denied");
    }

    private PermissionCheckResponse check(PermissionRequestContext context, String actionCode) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("SETTING");
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
