package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class CluePermissionGuard {

    private final PermissionService permissionService;
    private final ClueMapper clueMapper;
    private final AuthService authService;

    public CluePermissionGuard(PermissionService permissionService,
                               ClueMapper clueMapper,
                               AuthService authService) {
        this.permissionService = permissionService;
        this.clueMapper = clueMapper;
        this.authService = authService;
    }

    public void checkView(PermissionRequestContext context, Long clueId) {
        assertAllowed(check(context, "VIEW", resolveOwnerId(clueId)), "clue view denied");
    }

    public boolean canView(PermissionRequestContext context, Long clueId) {
        return check(context, "VIEW", resolveOwnerId(clueId)).isAllowed();
    }

    public void checkCreate(PermissionRequestContext context) {
        assertAllowed(check(context, "CREATE", context == null ? null : context.getCurrentUserId()), "clue create denied");
    }

    public void checkAssign(PermissionRequestContext context, Long clueId) {
        assertAllowed(check(context, "ASSIGN", resolveOwnerId(clueId)), "clue assign denied");
    }

    public void checkRecycle(PermissionRequestContext context, Long clueId) {
        assertAllowed(check(context, "RECYCLE", resolveOwnerId(clueId)), "clue recycle denied");
    }

    private PermissionCheckResponse check(PermissionRequestContext context, String actionCode, Long resourceOwnerId) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("CLUE");
        request.setActionCode(actionCode);
        request.setRoleCode(context.getRoleCode());
        request.setDataScope(context.getDataScope());
        request.setCurrentUserId(context.getCurrentUserId());
        request.setCurrentStoreId(context.getCurrentStoreId());
        request.setResourceStoreId(authService.resolveStoreId(resourceOwnerId));
        request.setTeamMemberIds(context.getTeamMemberIds());
        request.setBoundCustomerUserId(context.getBoundCustomerUserId());
        request.setResourceOwnerId(resourceOwnerId);
        return permissionService.check(request);
    }

    private Long resolveOwnerId(Long clueId) {
        if (clueId == null || clueId <= 0) {
            return null;
        }
        Clue clue = clueMapper.selectById(clueId);
        if (clue == null) {
            throw new BusinessException("clue not found");
        }
        return clue.getCurrentOwnerId();
    }

    private void assertAllowed(PermissionCheckResponse response, String messagePrefix) {
        if (!response.isAllowed()) {
            throw new BusinessException(messagePrefix + ": " + response.getReason());
        }
    }
}
