package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanOrderPermissionGuardTest {

    @Mock
    private PermissionService permissionService;

    @Mock
    private PlanOrderMapper planOrderMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ClueMapper clueMapper;

    @Mock
    private AuthService authService;

    private PlanOrderPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PlanOrderPermissionGuard(permissionService, planOrderMapper, orderMapper, clueMapper, authService);
    }

    @Test
    void shouldRejectPlanOrderViewWhenPermissionDenied() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(1L);
        planOrder.setOrderId(2L);
        when(planOrderMapper.selectById(1L)).thenReturn(planOrder);
        Order order = new Order();
        order.setId(2L);
        order.setClueId(3L);
        when(orderMapper.selectById(2L)).thenReturn(order);
        Clue clue = new Clue();
        clue.setId(3L);
        clue.setCurrentOwnerId(1002L);
        when(clueMapper.selectById(3L)).thenReturn(clue);
        when(authService.resolveStoreId(1002L)).thenReturn(11L);
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(false, null, "SELF", "ABAC scope rejected"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("PRIVATE_DOMAIN_SERVICE");
        context.setCurrentUserId(1001L);

        assertThatThrownBy(() -> guard.checkView(context, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("plan order view denied");
    }

    @Test
    void shouldAllowAssignRoleWhenPermissionMatches() {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(1L);
        planOrder.setOrderId(2L);
        when(planOrderMapper.selectById(1L)).thenReturn(planOrder);
        Order order = new Order();
        order.setId(2L);
        order.setClueId(3L);
        when(orderMapper.selectById(2L)).thenReturn(order);
        Clue clue = new Clue();
        clue.setId(3L);
        clue.setCurrentOwnerId(1001L);
        when(clueMapper.selectById(3L)).thenReturn(clue);
        when(authService.resolveStoreId(1001L)).thenReturn(10L);
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true, "PLANORDER:ASSIGN_ROLE:STORE_SERVICE:STORE", "STORE", "allowed"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_SERVICE");
        context.setCurrentStoreId(10L);
        context.setResourceStoreId(10L);

        guard.checkAssignRole(context, 1L);
    }
}
