package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderPermissionGuardTest {

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuthService authService;

    @Mock
    private OrderPermissionResourceResolver resourceResolver;

    private OrderPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new OrderPermissionGuard(permissionService, authService, resourceResolver);
    }

    @Test
    void shouldRejectOrderUpdateWhenPermissionDenied() {
        when(resourceResolver.resolveOrderStoreScopeOwnerId(1L)).thenReturn(1002L);
        when(authService.resolveStoreId(1002L)).thenReturn(11L);
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(false, null, "STORE", "ABAC scope rejected"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_SERVICE");
        context.setDataScope("STORE");
        context.setCurrentStoreId(10L);
        context.setResourceStoreId(11L);

        assertThatThrownBy(() -> guard.checkUpdate(context, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("order update denied");
    }

    @Test
    void shouldUseDirectOwnerForTeamScopeOrderView() {
        when(resourceResolver.resolveOrderOwnerId(1L)).thenReturn(1001L);
        when(authService.resolveStoreId(1001L)).thenReturn(10L);
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true, "ORDER:VIEW:ONLINE_CUSTOMER_SERVICE:TEAM", "TEAM", "allowed"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("ONLINE_CUSTOMER_SERVICE");
        context.setDataScope("TEAM");
        context.setCurrentUserId(1001L);

        guard.checkView(context, 1L);
    }

    @Test
    void shouldAllowCreateWhenPermissionMatches() {
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true, "ORDER:UPDATE:STORE_SERVICE:STORE", "STORE", "allowed"));
        when(resourceResolver.resolveClueOwnerId(2L)).thenReturn(1001L);
        when(authService.resolveStoreId(1001L)).thenReturn(10L);

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_SERVICE");
        context.setDataScope("STORE");
        context.setCurrentStoreId(10L);
        context.setResourceStoreId(10L);

        OrderCreateDTO request = new OrderCreateDTO();
        request.setClueId(2L);

        guard.checkCreate(context, request);
    }

    @Test
    void shouldRejectFinanceBusinessUpdateEvenIfPolicyWouldAllow() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("FINANCE");
        context.setDataScope("ALL");

        assertThatThrownBy(() -> guard.checkUpdate(context, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ledger-only");
        assertThatThrownBy(() -> guard.checkFinish(context, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ledger-only");
        verifyNoInteractions(permissionService);
    }
}
