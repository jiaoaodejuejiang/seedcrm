package com.seedcrm.crm.planorder.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.PlanOrderPermissionGuard;
import com.seedcrm.crm.planorder.dto.PlanOrderServiceFormStateResponse;
import com.seedcrm.crm.planorder.service.PlanOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanOrderControllerTest {

    @Mock
    private PlanOrderService planOrderService;

    @Mock
    private PermissionRequestContextResolver permissionRequestContextResolver;

    @Mock
    private PlanOrderPermissionGuard planOrderPermissionGuard;

    @Mock
    private HttpServletRequest request;

    private PlanOrderController controller;

    @BeforeEach
    void setUp() {
        controller = new PlanOrderController(planOrderService, permissionRequestContextResolver, planOrderPermissionGuard);
    }

    @Test
    void serviceFormStateShouldCheckViewPermissionAndReturnReadModel() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_SERVICE");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        PlanOrderServiceFormStateResponse state = new PlanOrderServiceFormStateResponse();
        state.setPlanOrderId(88L);
        state.setOrderId(188L);
        state.setPrinted(true);
        when(planOrderService.getServiceFormState(88L)).thenReturn(state);

        ApiResponse<PlanOrderServiceFormStateResponse> response = controller.serviceFormState(88L, request);

        verify(planOrderPermissionGuard).checkView(context, 88L);
        assertThat(response.getData().getPlanOrderId()).isEqualTo(88L);
        assertThat(response.getData().getPrinted()).isTrue();
    }
}
