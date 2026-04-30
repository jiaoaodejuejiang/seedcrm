package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class SchedulerModuleGuardTest {

    @Mock
    private PermissionService permissionService;

    private SchedulerModuleGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SchedulerModuleGuard(permissionService);
    }

    @Test
    void shouldPassPartnerScopeIntoSchedulerPermissionCheck() {
        when(permissionService.check(any()))
                .thenReturn(new PermissionCheckResponse(true, "SCHEDULER:VIEW:PARTNER_APP:PARTNER", "PARTNER", "allowed"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("PARTNER_APP");
        context.setDataScope("PARTNER");
        context.setCurrentPartnerCode("DISTRIBUTION");
        context.setResourcePartnerCode("DISTRIBUTION");

        guard.checkView(context);

        ArgumentCaptor<PermissionCheckRequest> captor = ArgumentCaptor.forClass(PermissionCheckRequest.class);
        verify(permissionService).check(captor.capture());
        PermissionCheckRequest request = captor.getValue();
        assertThat(request.getModuleCode()).isEqualTo("SCHEDULER");
        assertThat(request.getActionCode()).isEqualTo("VIEW");
        assertThat(request.getRoleCode()).isEqualTo("PARTNER_APP");
        assertThat(request.getDataScope()).isEqualTo("PARTNER");
        assertThat(request.getCurrentPartnerCode()).isEqualTo("DISTRIBUTION");
        assertThat(request.getResourcePartnerCode()).isEqualTo("DISTRIBUTION");
    }
}
