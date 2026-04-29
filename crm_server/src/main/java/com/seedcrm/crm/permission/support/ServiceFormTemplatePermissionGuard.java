package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ServiceFormTemplatePermissionGuard {

    private final PermissionService permissionService;

    public ServiceFormTemplatePermissionGuard(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void checkView(PermissionRequestContext context, String storeName) {
        validateStoreScope(context, storeName);
        assertAllowed(check(context, "VIEW"), "service form template view denied");
    }

    public void checkCatalog(PermissionRequestContext context) {
        validateManagerRole(context);
        assertAllowed(check(context, "TEMPLATE_VIEW"), "service form template catalog denied");
    }

    public void checkTemplateManage(PermissionRequestContext context) {
        validateAdminRole(context);
        assertAllowed(check(context, "TEMPLATE_MANAGE"), "service form template manage denied");
    }

    public void checkBindingUpdate(PermissionRequestContext context, String storeName) {
        validateManagerRole(context);
        validateStoreScope(context, storeName);
        assertAllowed(check(context, "TEMPLATE_BIND"), "service form template binding denied");
    }

    private PermissionCheckResponse check(PermissionRequestContext context, String actionCode) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("PLANORDER");
        request.setActionCode(actionCode);
        request.setRoleCode(context.getRoleCode());
        request.setDataScope(context.getDataScope());
        request.setCurrentUserId(context.getCurrentUserId());
        request.setCurrentStoreId(context.getCurrentStoreId());
        request.setResourceStoreId(context.getCurrentStoreId());
        request.setTeamMemberIds(context.getTeamMemberIds());
        request.setBoundCustomerUserId(context.getBoundCustomerUserId());
        request.setResourceOwnerId(context.getCurrentUserId());
        return permissionService.check(request);
    }

    private void validateAdminRole(PermissionRequestContext context) {
        String roleCode = context == null || context.getRoleCode() == null ? "" : context.getRoleCode();
        if (!"ADMIN".equalsIgnoreCase(roleCode)) {
            throw new BusinessException("服务单模板全局编辑和发布仅限管理员操作");
        }
    }

    private void validateManagerRole(PermissionRequestContext context) {
        String roleCode = context == null || context.getRoleCode() == null ? "" : context.getRoleCode();
        if (!"ADMIN".equalsIgnoreCase(roleCode) && !"STORE_MANAGER".equalsIgnoreCase(roleCode)) {
            throw new BusinessException("服务单模板配置仅限管理员或店长操作");
        }
    }

    private void validateStoreScope(PermissionRequestContext context, String storeName) {
        if (context == null || !"STORE".equalsIgnoreCase(context.getDataScope()) || !StringUtils.hasText(storeName)) {
            return;
        }
        String currentStoreName = context.getCurrentStoreName();
        if (!StringUtils.hasText(currentStoreName) || !currentStoreName.trim().equalsIgnoreCase(storeName.trim())) {
            throw new BusinessException("只能配置或查看当前门店的服务单模板");
        }
    }

    private void assertAllowed(PermissionCheckResponse response, String messagePrefix) {
        if (!response.isAllowed()) {
            throw new BusinessException(messagePrefix + ": " + response.getReason());
        }
    }
}
