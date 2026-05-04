package com.seedcrm.crm.permission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.entity.PermissionPolicy;
import com.seedcrm.crm.permission.service.PermissionService;
import com.seedcrm.crm.permission.support.PermissionModuleGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PermissionControllerTest {

    private PermissionService permissionService;
    private PermissionRequestContextResolver resolver;
    private PermissionModuleGuard permissionModuleGuard;
    private PermissionController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        permissionService = Mockito.mock(PermissionService.class);
        resolver = Mockito.mock(PermissionRequestContextResolver.class);
        permissionModuleGuard = Mockito.mock(PermissionModuleGuard.class);
        controller = new PermissionController(permissionService, resolver, permissionModuleGuard);
        request = Mockito.mock(HttpServletRequest.class);
    }

    @Test
    void storeManagerShouldReadOnlyFilteredStoreRoleMatrixPolicies() {
        PermissionRequestContext context = context("STORE_MANAGER");
        when(resolver.resolve(request)).thenReturn(context);
        when(permissionService.listPolicies()).thenReturn(List.of(
                policy("ORDER", "VIEW", "STORE_SERVICE"),
                policy("PLANORDER", "UPDATE", "PHOTO_SELECTOR"),
                policy("PERMISSION", "VIEW", "ADMIN"),
                policy("ORDER", "VIEW", "FINANCE")));

        ApiResponse<List<PermissionPolicy>> response = controller.listPolicies(request);

        assertThat(response.getData()).extracting(PermissionPolicy::getRoleCode)
                .containsExactly("STORE_SERVICE", "PHOTO_SELECTOR");
        verify(permissionModuleGuard, never()).checkView(context);
    }

    @Test
    void adminShouldUsePermissionModuleGuardForFullPolicyList() {
        PermissionRequestContext context = context("ADMIN");
        when(resolver.resolve(request)).thenReturn(context);
        when(permissionService.listPolicies()).thenReturn(List.of(policy("PERMISSION", "VIEW", "ADMIN")));

        controller.listPolicies(request);

        verify(permissionModuleGuard).checkView(context);
    }

    private PermissionRequestContext context(String roleCode) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(roleCode);
        context.setCurrentUserId(7L);
        return context;
    }

    private PermissionPolicy policy(String moduleCode, String actionCode, String roleCode) {
        PermissionPolicy policy = new PermissionPolicy();
        policy.setModuleCode(moduleCode);
        policy.setActionCode(actionCode);
        policy.setRoleCode(roleCode);
        policy.setDataScope("STORE");
        policy.setIsEnabled(1);
        return policy;
    }
}
