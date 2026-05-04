package com.seedcrm.crm.finance.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.permission.support.FinancePermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceControllerTest {

    @Mock
    private FinanceService financeService;

    @Mock
    private PermissionRequestContextResolver permissionRequestContextResolver;

    @Mock
    private FinancePermissionGuard financePermissionGuard;

    @Mock
    private HttpServletRequest request;

    private FinanceController controller;

    @BeforeEach
    void setUp() {
        controller = new FinanceController(financeService, permissionRequestContextResolver, financePermissionGuard);
    }

    @Test
    void storeRoleShouldBeRejectedFromBalance() {
        PermissionRequestContext context = context("STORE_SERVICE");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        doThrow(new BusinessException("finance view denied"))
                .when(financePermissionGuard).checkView(context);

        assertThatThrownBy(() -> controller.balance("USER", 7L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("finance view denied");
    }

    @Test
    void financeRoleShouldPassBalanceGuard() {
        PermissionRequestContext context = context("FINANCE");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);

        controller.balance("USER", 7L, request);

        verify(financePermissionGuard).checkView(context);
        verify(financeService).getBalance(AccountOwnerType.USER, 7L);
    }

    @Test
    void storeRoleShouldBeRejectedFromFinanceCheck() {
        PermissionRequestContext context = context("STORE_SERVICE");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        doThrow(new BusinessException("finance update denied"))
                .when(financePermissionGuard).checkUpdate(context);

        assertThatThrownBy(() -> controller.check(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("finance update denied");
    }

    private PermissionRequestContext context(String roleCode) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(roleCode);
        context.setCurrentUserId(7L);
        return context;
    }
}
