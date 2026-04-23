package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionModuleGuardTest {

    @Mock
    private PermissionService permissionService;

    private PermissionModuleGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PermissionModuleGuard(permissionService);
    }

    @Test
    void shouldRejectPermissionUpdateWhenPolicyDenied() {
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(false, null, "ALL", "no matching RBAC policy"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("CLUE_MANAGER");

        assertThatThrownBy(() -> guard.checkUpdate(context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("permission update denied");
    }

    @Test
    void shouldAllowPermissionViewForAdmin() {
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true, "PERMISSION:VIEW:ADMIN:ALL", "ALL", "allowed"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("ADMIN");

        guard.checkView(context);
    }
}
