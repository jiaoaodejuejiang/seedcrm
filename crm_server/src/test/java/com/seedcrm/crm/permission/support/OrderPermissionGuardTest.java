package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
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
    private OrderMapper orderMapper;

    @Mock
    private ClueMapper clueMapper;

    @Mock
    private AuthService authService;

    private OrderPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new OrderPermissionGuard(permissionService, orderMapper, clueMapper, authService);
    }

    @Test
    void shouldRejectOrderUpdateWhenPermissionDenied() {
        Order order = new Order();
        order.setId(1L);
        order.setClueId(2L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        Clue clue = new Clue();
        clue.setId(2L);
        clue.setCurrentOwnerId(1002L);
        when(clueMapper.selectById(2L)).thenReturn(clue);
        when(authService.resolveStoreId(1002L)).thenReturn(11L);
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(false, null, "STORE", "ABAC scope rejected"));

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_SERVICE");
        context.setCurrentStoreId(10L);
        context.setResourceStoreId(11L);

        assertThatThrownBy(() -> guard.checkUpdate(context, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("order update denied");
    }

    @Test
    void shouldAllowCreateWhenPermissionMatches() {
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true, "ORDER:UPDATE:STORE_SERVICE:STORE", "STORE", "allowed"));
        Clue clue = new Clue();
        clue.setId(2L);
        clue.setCurrentOwnerId(1001L);
        when(clueMapper.selectById(2L)).thenReturn(clue);
        when(authService.resolveStoreId(1001L)).thenReturn(10L);

        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_SERVICE");
        context.setCurrentStoreId(10L);
        context.setResourceStoreId(10L);

        OrderCreateDTO request = new OrderCreateDTO();
        request.setClueId(2L);

        guard.checkCreate(context, request);
    }
}
