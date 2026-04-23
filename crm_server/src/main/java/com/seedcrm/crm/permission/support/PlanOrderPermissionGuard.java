package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import org.springframework.stereotype.Component;

@Component
public class PlanOrderPermissionGuard {

    private final PermissionService permissionService;
    private final PlanOrderMapper planOrderMapper;
    private final OrderMapper orderMapper;
    private final ClueMapper clueMapper;
    private final AuthService authService;

    public PlanOrderPermissionGuard(PermissionService permissionService,
                                    PlanOrderMapper planOrderMapper,
                                    OrderMapper orderMapper,
                                    ClueMapper clueMapper,
                                    AuthService authService) {
        this.permissionService = permissionService;
        this.planOrderMapper = planOrderMapper;
        this.orderMapper = orderMapper;
        this.clueMapper = clueMapper;
        this.authService = authService;
    }

    public void checkCreate(PermissionRequestContext context, Long orderId) {
        PermissionCheckRequest request = buildCheckRequest(context, "PLANORDER", "CREATE", resolveOrderOwnerId(orderId));
        assertAllowed(permissionService.check(request), "plan order create denied");
    }

    public void checkUpdate(PermissionRequestContext context, Long planOrderId) {
        PermissionCheckRequest request = buildCheckRequest(context, "PLANORDER", "UPDATE", resolvePlanOrderOwnerId(planOrderId));
        assertAllowed(permissionService.check(request), "plan order update denied");
    }

    public void checkView(PermissionRequestContext context, Long planOrderId) {
        assertAllowed(checkViewPermission(context, planOrderId), "plan order view denied");
    }

    public void checkAssignRole(PermissionRequestContext context, Long planOrderId) {
        PermissionCheckRequest request = buildCheckRequest(context, "PLANORDER", "ASSIGN_ROLE", resolvePlanOrderOwnerId(planOrderId));
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
        PermissionCheckRequest request = buildCheckRequest(context, "PLANORDER", "VIEW", resolvePlanOrderOwnerId(planOrderId));
        return permissionService.check(request);
    }

    private Long resolvePlanOrderOwnerId(Long planOrderId) {
        if (planOrderId == null || planOrderId <= 0) {
            throw new BusinessException("planOrderId is required");
        }
        PlanOrder planOrder = planOrderMapper.selectById(planOrderId);
        if (planOrder == null) {
            throw new BusinessException("plan order not found");
        }
        return resolveOrderOwnerId(planOrder.getOrderId());
    }

    private Long resolveOrderOwnerId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException("orderId is required");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        if (order.getClueId() == null) {
            return null;
        }
        Clue clue = clueMapper.selectById(order.getClueId());
        return clue == null ? null : clue.getCurrentOwnerId();
    }
}
