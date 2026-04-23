package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class OrderPermissionGuard {

    private final PermissionService permissionService;
    private final OrderMapper orderMapper;
    private final ClueMapper clueMapper;

    public OrderPermissionGuard(PermissionService permissionService,
                                OrderMapper orderMapper,
                                ClueMapper clueMapper) {
        this.permissionService = permissionService;
        this.orderMapper = orderMapper;
        this.clueMapper = clueMapper;
    }

    public void checkCreate(PermissionRequestContext context, OrderCreateDTO request) {
        Long clueOwnerId = resolveClueOwnerId(request == null ? null : request.getClueId());
        PermissionCheckRequest checkRequest = buildCheckRequest(context, "ORDER", "UPDATE", clueOwnerId);
        assertAllowed(permissionService.check(checkRequest), "order create denied");
    }

    public void checkUpdate(PermissionRequestContext context, Long orderId) {
        Order order = getOrderOrThrow(orderId);
        PermissionCheckRequest checkRequest = buildCheckRequest(context, "ORDER", "UPDATE", resolveClueOwnerId(order.getClueId()));
        assertAllowed(permissionService.check(checkRequest), "order update denied");
    }

    public void checkView(PermissionRequestContext context, Long orderId) {
        assertAllowed(checkViewPermission(context, orderId), "order view denied");
    }

    public boolean canView(PermissionRequestContext context, Long orderId) {
        return checkViewPermission(context, orderId).isAllowed();
    }

    public void checkFinish(PermissionRequestContext context, Long orderId) {
        Order order = getOrderOrThrow(orderId);
        PermissionCheckRequest checkRequest = buildCheckRequest(context, "ORDER", "FINISH", resolveClueOwnerId(order.getClueId()));
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
        request.setResourceStoreId(context.getResourceStoreId());
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
        Order order = getOrderOrThrow(orderId);
        PermissionCheckRequest checkRequest = buildCheckRequest(context, "ORDER", "VIEW", resolveClueOwnerId(order.getClueId()));
        return permissionService.check(checkRequest);
    }

    private Order getOrderOrThrow(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException("orderId is required");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        return order;
    }

    private Long resolveClueOwnerId(Long clueId) {
        if (clueId == null) {
            return null;
        }
        Clue clue = clueMapper.selectById(clueId);
        return clue == null ? null : clue.getCurrentOwnerId();
    }
}
