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
class ServiceFormTemplatePermissionGuardTest {

    @Mock
    private PermissionService permissionService;

    private ServiceFormTemplatePermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ServiceFormTemplatePermissionGuard(permissionService);
    }

    @Test
    void shouldUseDedicatedTemplateManagePolicyForAdminTemplateEditing() {
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true,
                "PLANORDER:TEMPLATE_MANAGE:ADMIN:ALL", "ALL", "allowed"));
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("ADMIN");
        context.setDataScope("ALL");

        guard.checkTemplateManage(context);

        ArgumentCaptor<PermissionCheckRequest> captor = ArgumentCaptor.forClass(PermissionCheckRequest.class);
        verify(permissionService).check(captor.capture());
        assertThat(captor.getValue().getModuleCode()).isEqualTo("PLANORDER");
        assertThat(captor.getValue().getActionCode()).isEqualTo("TEMPLATE_MANAGE");
        assertThat(captor.getValue().getRoleCode()).isEqualTo("ADMIN");
    }

    @Test
    void shouldRejectGlobalTemplateEditingForStoreManagerBeforePolicyCheck() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_MANAGER");
        context.setDataScope("STORE");

        assertThatThrownBy(() -> guard.checkTemplateManage(context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅限管理员");
        verify(permissionService, never()).check(any());
    }

    @Test
    void shouldRejectOtherStoreBindingForStoreScopedManagerBeforePolicyCheck() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_MANAGER");
        context.setDataScope("STORE");
        context.setCurrentStoreName("静安门店");

        assertThatThrownBy(() -> guard.checkBindingUpdate(context, "浦东门店"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前门店");
        verify(permissionService, never()).check(any());
    }

    @Test
    void shouldAllowCurrentStoreBindingWhenRbacAndAbacMatch() {
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true,
                "PLANORDER:TEMPLATE_BIND:STORE_MANAGER:STORE", "STORE", "allowed"));
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_MANAGER");
        context.setDataScope("STORE");
        context.setCurrentStoreId(10L);
        context.setCurrentStoreName("静安门店");

        guard.checkBindingUpdate(context, "静安门店");

        ArgumentCaptor<PermissionCheckRequest> captor = ArgumentCaptor.forClass(PermissionCheckRequest.class);
        verify(permissionService).check(captor.capture());
        assertThat(captor.getValue().getActionCode()).isEqualTo("TEMPLATE_BIND");
        assertThat(captor.getValue().getCurrentStoreId()).isEqualTo(10L);
        assertThat(captor.getValue().getResourceStoreId()).isEqualTo(10L);
    }
}
