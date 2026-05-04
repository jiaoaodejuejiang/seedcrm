package com.seedcrm.crm.workbench.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.CluePermissionGuard;
import com.seedcrm.crm.permission.support.CustomerPermissionGuard;
import com.seedcrm.crm.permission.support.FinancePermissionGuard;
import com.seedcrm.crm.permission.support.OrderPermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.PlanOrderPermissionGuard;
import com.seedcrm.crm.permission.support.SensitiveDataProjectionService;
import com.seedcrm.crm.scheduler.service.SchedulerService;
import com.seedcrm.crm.scheduler.support.SchedulerSensitiveDataMasker;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.OrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderWorkbenchResponse;
import com.seedcrm.crm.workbench.service.WorkbenchService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkbenchControllerAmountMaskingTest {

    @Mock
    private WorkbenchService workbenchService;

    @Mock
    private PermissionRequestContextResolver permissionRequestContextResolver;

    @Mock
    private CluePermissionGuard cluePermissionGuard;

    @Mock
    private CustomerPermissionGuard customerPermissionGuard;

    @Mock
    private OrderPermissionGuard orderPermissionGuard;

    @Mock
    private PlanOrderPermissionGuard planOrderPermissionGuard;

    @Mock
    private FinancePermissionGuard financePermissionGuard;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private SchedulerService schedulerService;

    @Mock
    private SchedulerSensitiveDataMasker schedulerSensitiveDataMasker;

    @Mock
    private HttpServletRequest request;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkbenchController controller;

    @BeforeEach
    void setUp() {
        controller = new WorkbenchController(
                workbenchService,
                permissionRequestContextResolver,
                cluePermissionGuard,
                customerPermissionGuard,
                orderPermissionGuard,
                planOrderPermissionGuard,
                financePermissionGuard,
                new SensitiveDataProjectionService(objectMapper, systemConfigService),
                schedulerService,
                schedulerSensitiveDataMasker);
        lenient().when(systemConfigService.getBoolean("amount.visibility.store_staff_hidden", true)).thenReturn(true);
        lenient().when(systemConfigService.getString(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void storeManagerWorkbenchDetailShouldHideBusinessAmountsButKeepServiceConfirmAmount() throws Exception {
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context("STORE_MANAGER"));
        when(workbenchService.getPlanOrderWorkbench(100L)).thenReturn(sampleWorkbench());

        ApiResponse<PlanOrderWorkbenchResponse> response = controller.planOrderDetail(100L, request);

        assertThat(response.getData().getSummary().getAmount()).isNull();
        assertThat(response.getData().getOrder().getAmount()).isNull();
        assertThat(response.getData().getOrder().getDeposit()).isNull();
        assertThat(response.getData().getOrder().getVerificationCode()).isNull();
        JsonNode serviceDetail = objectMapper.readTree(response.getData().getOrder().getServiceDetailJson());
        assertThat(serviceDetail.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("1288.00");
    }

    @Test
    void photoSelectorWorkbenchDetailShouldHideBusinessAmountsButKeepServiceConfirmAmount() throws Exception {
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context("PHOTO_SELECTOR"));
        when(workbenchService.getPlanOrderWorkbench(100L)).thenReturn(sampleWorkbench());

        ApiResponse<PlanOrderWorkbenchResponse> response = controller.planOrderDetail(100L, request);

        assertThat(response.getData().getSummary().getAmount()).isNull();
        assertThat(response.getData().getOrder().getAmount()).isNull();
        assertThat(response.getData().getOrder().getDeposit()).isNull();
        assertThat(response.getData().getOrder().getVerificationCode()).isNull();
        JsonNode serviceDetail = objectMapper.readTree(response.getData().getOrder().getServiceDetailJson());
        assertThat(serviceDetail.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("1288.00");
    }

    @Test
    void storeServiceWorkbenchDetailShouldHideBusinessAndServiceConfirmAmounts() throws Exception {
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context("STORE_SERVICE"));
        when(workbenchService.getPlanOrderWorkbench(100L)).thenReturn(sampleWorkbench());

        ApiResponse<PlanOrderWorkbenchResponse> response = controller.planOrderDetail(100L, request);

        assertThat(response.getData().getSummary().getAmount()).isNull();
        assertThat(response.getData().getOrder().getAmount()).isNull();
        assertThat(response.getData().getOrder().getDeposit()).isNull();
        assertThat(response.getData().getOrder().getVerificationCode()).isNull();
        JsonNode serviceDetail = objectMapper.readTree(response.getData().getOrder().getServiceDetailJson());
        assertThat(serviceDetail.path("serviceConfirmAmount").isNull()).isTrue();
        assertThat(serviceDetail.path("serviceTemplate").path("config").path("price").isNull()).isTrue();
    }

    @Test
    void storeServiceOrderListShouldHideBusinessAndServiceConfirmAmounts() throws Exception {
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context("STORE_SERVICE"));
        when(workbenchService.listOrders(null, null, null)).thenReturn(List.of(sampleOrder()));
        when(orderPermissionGuard.canView(any(PermissionRequestContext.class), eq(11L))).thenReturn(true);

        ApiResponse<List<OrderItemResponse>> response = controller.orders(null, null, null, request);

        OrderItemResponse order = response.getData().get(0);
        assertThat(order.getAmount()).isNull();
        assertThat(order.getDeposit()).isNull();
        assertThat(order.getVerificationCode()).isNull();
        JsonNode serviceDetail = objectMapper.readTree(order.getServiceDetailJson());
        assertThat(serviceDetail.path("serviceConfirmAmount").isNull()).isTrue();
        assertThat(serviceDetail.path("serviceTemplate").path("config").path("price").isNull()).isTrue();
    }

    @Test
    void adminWorkbenchDetailShouldKeepAllAmounts() throws Exception {
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context("ADMIN"));
        when(workbenchService.getPlanOrderWorkbench(100L)).thenReturn(sampleWorkbench());

        ApiResponse<PlanOrderWorkbenchResponse> response = controller.planOrderDetail(100L, request);

        assertThat(response.getData().getSummary().getAmount()).isEqualByComparingTo("1999.00");
        assertThat(response.getData().getOrder().getAmount()).isEqualByComparingTo("1999.00");
        assertThat(response.getData().getOrder().getDeposit()).isEqualByComparingTo("299.00");
        assertThat(response.getData().getOrder().getVerificationCode()).isEqualTo("VC-8888");
        JsonNode serviceDetail = objectMapper.readTree(response.getData().getOrder().getServiceDetailJson());
        assertThat(serviceDetail.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("1288.00");
    }

    @Test
    void storeRoleShouldBeRejectedFromFinanceOverview() {
        PermissionRequestContext context = context("STORE_SERVICE");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        doThrow(new BusinessException("finance view denied"))
                .when(financePermissionGuard).checkView(context);

        assertThatThrownBy(() -> controller.financeOverview(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("finance view denied");
    }

    @Test
    void storeRoleShouldBeRejectedFromDistributorBoard() {
        PermissionRequestContext context = context("STORE_SERVICE");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
        doThrow(new BusinessException("finance view denied"))
                .when(financePermissionGuard).checkView(context);

        assertThatThrownBy(() -> controller.distributors(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("finance view denied");
    }

    private PermissionRequestContext context(String roleCode) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(roleCode);
        context.setCurrentUserId(7L);
        return context;
    }

    private PlanOrderWorkbenchResponse sampleWorkbench() {
        PlanOrderItemResponse summary = new PlanOrderItemResponse();
        summary.setPlanOrderId(100L);
        summary.setOrderId(11L);
        summary.setAmount(new BigDecimal("1999.00"));

        OrderItemResponse order = new OrderItemResponse();
        order.setId(11L);
        order.setAmount(new BigDecimal("1999.00"));
        order.setDeposit(new BigDecimal("299.00"));
        order.setVerificationCode("VC-8888");
        order.setServiceDetailJson("""
                {
                  "serviceRequirement": "portrait",
                  "serviceConfirmAmount": 1288.00,
                  "serviceTemplate": {
                    "config": {
                      "price": 99
                    }
                  }
                }
                """);

        PlanOrderWorkbenchResponse response = new PlanOrderWorkbenchResponse();
        response.setSummary(summary);
        response.setOrder(order);
        return response;
    }

    private OrderItemResponse sampleOrder() {
        OrderItemResponse order = new OrderItemResponse();
        order.setId(11L);
        order.setStoreName("静安门店");
        order.setAmount(new BigDecimal("1999.00"));
        order.setDeposit(new BigDecimal("299.00"));
        order.setVerificationCode("VC-8888");
        order.setServiceDetailJson("""
                {
                  "serviceRequirement": "portrait",
                  "serviceConfirmAmount": 1288.00,
                  "serviceTemplate": {
                    "config": {
                      "price": 99
                    }
                  }
                }
                """);
        return order;
    }
}
