package com.seedcrm.crm.workbench.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.CluePermissionGuard;
import com.seedcrm.crm.permission.support.OrderPermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.PlanOrderPermissionGuard;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.ClueItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CustomerProfileResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.DistributorBoardItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.FinanceOverviewResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.OrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderWorkbenchResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StaffRoleOptionResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StoreLiveCodePreviewResponse;
import com.seedcrm.crm.workbench.service.WorkbenchService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workbench")
public class WorkbenchController {

    private final WorkbenchService workbenchService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final CluePermissionGuard cluePermissionGuard;
    private final OrderPermissionGuard orderPermissionGuard;
    private final PlanOrderPermissionGuard planOrderPermissionGuard;

    public WorkbenchController(WorkbenchService workbenchService,
                               PermissionRequestContextResolver permissionRequestContextResolver,
                               CluePermissionGuard cluePermissionGuard,
                               OrderPermissionGuard orderPermissionGuard,
                               PlanOrderPermissionGuard planOrderPermissionGuard) {
        this.workbenchService = workbenchService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.cluePermissionGuard = cluePermissionGuard;
        this.orderPermissionGuard = orderPermissionGuard;
        this.planOrderPermissionGuard = planOrderPermissionGuard;
    }

    @GetMapping("/clues")
    public ApiResponse<List<ClueItemResponse>> clues(@RequestParam(required = false) String sourceChannel,
                                                     @RequestParam(required = false) String productSourceType,
                                                     @RequestParam(required = false) String status,
                                                     HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        List<ClueItemResponse> clues = workbenchService.listClues(sourceChannel, productSourceType, status).stream()
                .filter(clue -> cluePermissionGuard.canView(context, clue.getId()))
                .collect(Collectors.toList());
        return ApiResponse.success(clues);
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderItemResponse>> orders(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String customerName,
                                                       @RequestParam(required = false) String customerPhone,
                                                       HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        List<OrderItemResponse> orders = workbenchService.listOrders(status, customerName, customerPhone).stream()
                .filter(order -> orderPermissionGuard.canView(context, order.getId()))
                .filter(order -> matchesStoreScope(context, order.getStoreName()))
                .collect(Collectors.toList());
        return ApiResponse.success(orders);
    }

    @GetMapping("/orders/{orderId}/wecom-live-code")
    public ApiResponse<StoreLiveCodePreviewResponse> orderWecomLiveCode(@PathVariable Long orderId,
                                                                        HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkView(context, orderId);
        return ApiResponse.success(workbenchService.getOrderLiveCodePreview(orderId));
    }

    @GetMapping("/plan-orders")
    public ApiResponse<List<PlanOrderItemResponse>> planOrders(@RequestParam(required = false) String status,
                                                               HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        List<PlanOrderItemResponse> planOrders = workbenchService.listPlanOrders(status).stream()
                .filter(planOrder -> planOrderPermissionGuard.canView(context, planOrder.getPlanOrderId()))
                .collect(Collectors.toList());
        return ApiResponse.success(planOrders);
    }

    private boolean matchesStoreScope(PermissionRequestContext context, String rowStoreName) {
        if (context == null || !"STORE".equalsIgnoreCase(context.getDataScope())) {
            return true;
        }
        if (context.getCurrentStoreName() == null || context.getCurrentStoreName().isBlank()) {
            return true;
        }
        return rowStoreName != null && context.getCurrentStoreName().trim().equalsIgnoreCase(rowStoreName.trim());
    }

    @GetMapping("/plan-orders/{planOrderId}")
    public ApiResponse<PlanOrderWorkbenchResponse> planOrderDetail(@PathVariable Long planOrderId,
                                                                   HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        planOrderPermissionGuard.checkView(context, planOrderId);
        return ApiResponse.success(workbenchService.getPlanOrderWorkbench(planOrderId));
    }

    @GetMapping("/customers/{customerId}")
    public ApiResponse<CustomerProfileResponse> customerDetail(@PathVariable Long customerId) {
        return ApiResponse.success(workbenchService.getCustomerProfile(customerId));
    }

    @GetMapping("/distributors")
    public ApiResponse<List<DistributorBoardItemResponse>> distributors() {
        return ApiResponse.success(workbenchService.listDistributors());
    }

    @GetMapping("/finance-overview")
    public ApiResponse<FinanceOverviewResponse> financeOverview() {
        return ApiResponse.success(workbenchService.getFinanceOverview());
    }

    @GetMapping("/staff-options")
    public ApiResponse<List<StaffRoleOptionResponse>> staffOptions() {
        return ApiResponse.success(workbenchService.listStaffOptions());
    }
}
