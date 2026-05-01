package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
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
    private AuthService authService;

    @Mock
    private OrderPermissionResourceResolver resourceResolver;

    private PlanOrderPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PlanOrderPermissionGuard(permissionService, authService, resourceResolver);
    }

    @Test
    void shouldRejectPlanOrderViewWhenPermissionDenied() {
        when(resourceResolver.resolvePlanOrderOwnerId(1L)).thenReturn(1002L);
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
        when(resourceResolver.resolvePlanOrderStoreScopeOwnerId(1L)).thenReturn(1001L);
        when(authService.resolveStoreId(1001L)).thenReturn(10L);
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true, "PLANORDER:ASSIGN_ROLE:STORE_SERVICE:STORE", "STORE", "allowed"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_SERVICE");
        context.setDataScope("STORE");
        context.setCurrentStoreId(10L);
        context.setResourceStoreId(10L);

        guard.checkAssignRole(context, 1L);
    }

    @Test
    void shouldUseDirectOwnerForSelfScopedPlanOrderView() {
        when(resourceResolver.resolvePlanOrderOwnerId(1L)).thenReturn(1001L);
        when(authService.resolveStoreId(1001L)).thenReturn(10L);
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true, "PLANORDER:VIEW:PRIVATE_DOMAIN_SERVICE:SELF", "SELF", "allowed"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("PRIVATE_DOMAIN_SERVICE");
        context.setDataScope("SELF");
        context.setCurrentUserId(1001L);

        guard.checkView(context, 1L);
    }

    @Test
    void shouldRejectFinancePlanOrderMutationEvenIfPolicyWouldAllow() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("FINANCE");
        context.setDataScope("ALL");

        assertThatThrownBy(() -> guard.checkUpdate(context, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ledger-only");
        assertThatThrownBy(() -> guard.checkAssignRole(context, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ledger-only");
        verifyNoInteractions(permissionService);
    }
}
