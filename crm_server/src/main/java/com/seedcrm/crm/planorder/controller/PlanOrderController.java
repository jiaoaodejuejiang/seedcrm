package com.seedcrm.crm.planorder.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.planorder.dto.PlanOrderActionDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderAssignRoleDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderCreateDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderDetailResponse;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.service.PlanOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/planOrder")
public class PlanOrderController {

    private final PlanOrderService planOrderService;

    public PlanOrderController(PlanOrderService planOrderService) {
        this.planOrderService = planOrderService;
    }

    @PostMapping("/create")
    public ApiResponse<PlanOrder> create(@RequestBody PlanOrderCreateDTO planOrderCreateDTO) {
        return ApiResponse.success(planOrderService.createPlanOrder(planOrderCreateDTO));
    }

    @PostMapping("/arrive")
    public ApiResponse<PlanOrder> arrive(@RequestBody PlanOrderActionDTO planOrderActionDTO) {
        return ApiResponse.success(planOrderService.arrive(planOrderActionDTO));
    }

    @PostMapping("/start")
    public ApiResponse<PlanOrder> start(@RequestBody PlanOrderActionDTO planOrderActionDTO) {
        return ApiResponse.success(planOrderService.start(planOrderActionDTO));
    }

    @PostMapping("/finish")
    public ApiResponse<PlanOrder> finish(@RequestBody PlanOrderActionDTO planOrderActionDTO) {
        return ApiResponse.success(planOrderService.finish(planOrderActionDTO));
    }

    @PostMapping("/assignRole")
    public ApiResponse<OrderRoleRecord> assignRole(@RequestBody PlanOrderAssignRoleDTO planOrderAssignRoleDTO) {
        return ApiResponse.success(planOrderService.assignRole(planOrderAssignRoleDTO));
    }

    @GetMapping("/detail")
    public ApiResponse<PlanOrderDetailResponse> detail(@RequestParam Long planOrderId) {
        return ApiResponse.success(planOrderService.getDetail(planOrderId));
    }
}
