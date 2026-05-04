package com.seedcrm.crm.distributor.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.distributor.dto.DistributorWithdrawApproveRequest;
import com.seedcrm.crm.distributor.service.DistributorService;
import com.seedcrm.crm.distributor.service.DistributorSettlementService;
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
class DistributorControllerTest {

    @Mock
    private DistributorService distributorService;

    @Mock
    private DistributorSettlementService distributorSettlementService;

    @Mock
    private PermissionRequestContextResolver permissionRequestContextResolver;

    @Mock
    private FinancePermissionGuard financePermissionGuard;

    @Mock
    private HttpServletRequest request;

    private DistributorController controller;

    @BeforeEach
    void setUp() {
        controller = new DistributorController(
                distributorService,
                distributorSettlementService,
                permissionRequestContextResolver,
                financePermissionGuard);
    }

    @Test
    void storeRoleShouldBeRejectedFromWithdrawApproval() {
        PermissionRequestContext context = context("STORE_SERVICE");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        doThrow(new BusinessException("finance update denied"))
                .when(financePermissionGuard).checkUpdate(context);

        DistributorWithdrawApproveRequest approveRequest = new DistributorWithdrawApproveRequest();
        approveRequest.setWithdrawId(12L);
        approveRequest.setStatus("APPROVED");

        assertThatThrownBy(() -> controller.approveWithdraw(approveRequest, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("finance update denied");
    }

    @Test
    void financeRoleShouldPassWithdrawApprovalGuard() {
        PermissionRequestContext context = context("FINANCE");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        DistributorWithdrawApproveRequest approveRequest = new DistributorWithdrawApproveRequest();
        approveRequest.setWithdrawId(12L);
        approveRequest.setStatus("APPROVED");

        controller.approveWithdraw(approveRequest, request);

        verify(financePermissionGuard).checkUpdate(context);
        verify(distributorSettlementService).approveWithdraw(12L, com.seedcrm.crm.distributor.enums.DistributorWithdrawStatus.APPROVED);
    }

    @Test
    void storeRoleShouldBeRejectedFromStats() {
        PermissionRequestContext context = context("STORE_SERVICE");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        doThrow(new BusinessException("finance view denied"))
                .when(financePermissionGuard).checkView(context);

        assertThatThrownBy(() -> controller.stats(7L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("finance view denied");
    }

    private PermissionRequestContext context(String roleCode) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(roleCode);
        context.setCurrentUserId(7L);
        return context;
    }
}
