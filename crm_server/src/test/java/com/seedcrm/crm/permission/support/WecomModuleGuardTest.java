package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WecomModuleGuardTest {

    @Mock
    private PermissionService permissionService;

    private WecomModuleGuard guard;

    @BeforeEach
    void setUp() {
        guard = new WecomModuleGuard(permissionService);
    }

    @Test
    void shouldUseWorkspacePolicyForPrivateDomainView() {
        when(permissionService.check(any()))
                .thenReturn(new PermissionCheckResponse(true, "WECOM:VIEW:PRIVATE_DOMAIN_SERVICE:ALL", "ALL", "allowed"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("PRIVATE_DOMAIN_SERVICE");
        context.setDataScope("SELF");
        context.setCurrentUserId(7L);

        guard.checkView(context);

        ArgumentCaptor<PermissionCheckRequest> captor = ArgumentCaptor.forClass(PermissionCheckRequest.class);
        verify(permissionService).check(captor.capture());
        assertThat(captor.getValue().getModuleCode()).isEqualTo("WECOM");
        assertThat(captor.getValue().getActionCode()).isEqualTo("VIEW");
        assertThat(captor.getValue().getRoleCode()).isEqualTo("PRIVATE_DOMAIN_SERVICE");
        assertThat(captor.getValue().getDataScope()).isNull();
    }

    @Test
    void shouldRejectWecomUpdateWhenPolicyDenied() {
        when(permissionService.check(any()))
                .thenReturn(new PermissionCheckResponse(false, null, "ALL", "ABAC scope rejected"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("PRIVATE_DOMAIN_SERVICE");
        context.setDataScope("SELF");

        assertThatThrownBy(() -> guard.checkUpdate(context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("wecom update denied");
    }

    @Test
    void shouldRejectPrivateDomainConfigManageBeforePermissionCheck() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("PRIVATE_DOMAIN_SERVICE");

        assertThatThrownBy(() -> guard.checkConfigManage(context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("admin required");

        verify(permissionService, never()).check(any());
    }

    @Test
    void shouldAllowAdminConfigManageWhenUpdateGranted() {
        when(permissionService.check(any()))
                .thenReturn(new PermissionCheckResponse(true, "WECOM:UPDATE:ADMIN:ALL", "ALL", "allowed"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("ADMIN");
        context.setCurrentUserId(1L);

        guard.checkConfigManage(context);

        ArgumentCaptor<PermissionCheckRequest> captor = ArgumentCaptor.forClass(PermissionCheckRequest.class);
        verify(permissionService).check(captor.capture());
        assertThat(captor.getValue().getActionCode()).isEqualTo("UPDATE");
        assertThat(captor.getValue().getRoleCode()).isEqualTo("ADMIN");
    }
}
