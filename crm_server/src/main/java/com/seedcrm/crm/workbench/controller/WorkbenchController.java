package com.seedcrm.crm.workbench.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.ClueItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CustomerProfileResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.DistributorBoardItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.FinanceOverviewResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.OrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderWorkbenchResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StaffRoleOptionResponse;
import com.seedcrm.crm.workbench.service.WorkbenchService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workbench")
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping("/clues")
    public ApiResponse<List<ClueItemResponse>> clues(@RequestParam(required = false) String sourceChannel,
                                                     @RequestParam(required = false) String status) {
        return ApiResponse.success(workbenchService.listClues(sourceChannel, status));
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderItemResponse>> orders(@RequestParam(required = false) String status) {
        return ApiResponse.success(workbenchService.listOrders(status));
    }

    @GetMapping("/plan-orders")
    public ApiResponse<List<PlanOrderItemResponse>> planOrders(@RequestParam(required = false) String status) {
        return ApiResponse.success(workbenchService.listPlanOrders(status));
    }

    @GetMapping("/plan-orders/{planOrderId}")
    public ApiResponse<PlanOrderWorkbenchResponse> planOrderDetail(@PathVariable Long planOrderId) {
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
