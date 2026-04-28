package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalaryModuleGuardTest {

    @Mock
    private PermissionService permissionService;

    private SalaryModuleGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SalaryModuleGuard(permissionService);
    }

    @Test
    void shouldBuildSalaryViewRequestWithTargetUserAsResourceOwner() {
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true, "SALARY:VIEW:CLUE_MANAGER:TEAM", "TEAM", "allowed"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("CLUE_MANAGER");
        context.setCurrentUserId(5001L);
        context.setTeamMemberIds(List.of(5001L, 1001L));

        guard.checkView(context, 1001L);

        ArgumentCaptor<PermissionCheckRequest> captor = ArgumentCaptor.forClass(PermissionCheckRequest.class);
        verify(permissionService).check(captor.capture());
        assertThat(captor.getValue().getModuleCode()).isEqualTo("SALARY");
        assertThat(captor.getValue().getActionCode()).isEqualTo("VIEW");
        assertThat(captor.getValue().getResourceOwnerId()).isEqualTo(1001L);
        assertThat(captor.getValue().getDataScope()).isNull();
        assertThat(captor.getValue().getBoundCustomerUserId()).isNull();
    }

    @Test
    void shouldRejectSalaryViewWhenPermissionDenied() {
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(false, null, "SELF", "ABAC scope rejected"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("ONLINE_CUSTOMER_SERVICE");
        context.setCurrentUserId(1001L);

        assertThatThrownBy(() -> guard.checkView(context, 1002L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("salary view denied");
    }
}
