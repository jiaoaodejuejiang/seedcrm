package com.seedcrm.crm.planorder.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.PlanOrderPermissionGuard;
import com.seedcrm.crm.planorder.dto.PlanOrderActionDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderAssignRoleDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderCreateDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderDetailResponse;
import com.seedcrm.crm.planorder.dto.PlanOrderResponse;
import com.seedcrm.crm.planorder.dto.PlanOrderSendServiceFormDTO;
import com.seedcrm.crm.planorder.service.PlanOrderService;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import jakarta.servlet.http.HttpServletRequest;
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
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final PlanOrderPermissionGuard planOrderPermissionGuard;

    public PlanOrderController(PlanOrderService planOrderService,
                               PermissionRequestContextResolver permissionRequestContextResolver,
                               PlanOrderPermissionGuard planOrderPermissionGuard) {
        this.planOrderService = planOrderService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.planOrderPermissionGuard = planOrderPermissionGuard;
    }

    @PostMapping("/create")
    public ApiResponse<PlanOrderResponse> create(@RequestBody PlanOrderCreateDTO planOrderCreateDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        planOrderPermissionGuard.checkCreate(context, planOrderCreateDTO == null ? null : planOrderCreateDTO.getOrderId());
        return ApiResponse.success(PlanOrderResponse.from(planOrderService.createPlanOrder(
                planOrderCreateDTO,
                context.getCurrentUserId(),
                context.getRoleCode())));
    }

    @PostMapping("/arrive")
    public ApiResponse<PlanOrderResponse> arrive(@RequestBody PlanOrderActionDTO planOrderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        planOrderPermissionGuard.checkUpdate(context, planOrderActionDTO == null ? null : planOrderActionDTO.getPlanOrderId());
        return ApiResponse.success(PlanOrderResponse.from(planOrderService.arrive(planOrderActionDTO)));
    }

    @PostMapping("/start")
    public ApiResponse<PlanOrderResponse> start(@RequestBody PlanOrderActionDTO planOrderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        planOrderPermissionGuard.checkUpdate(context, planOrderActionDTO == null ? null : planOrderActionDTO.getPlanOrderId());
        return ApiResponse.success(PlanOrderResponse.from(planOrderService.start(planOrderActionDTO)));
    }

    @PostMapping("/finish")
    public ApiResponse<PlanOrderResponse> finish(@RequestBody PlanOrderActionDTO planOrderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        planOrderPermissionGuard.checkUpdate(context, planOrderActionDTO == null ? null : planOrderActionDTO.getPlanOrderId());
        return ApiResponse.success(PlanOrderResponse.from(planOrderService.finish(planOrderActionDTO, context.getCurrentUserId())));
    }

    @PostMapping("/assignRole")
    public ApiResponse<com.seedcrm.crm.planorder.entity.OrderRoleRecord> assignRole(
            @RequestBody PlanOrderAssignRoleDTO planOrderAssignRoleDTO,
            HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        planOrderPermissionGuard.checkAssignRole(context,
                planOrderAssignRoleDTO == null ? null : planOrderAssignRoleDTO.getPlanOrderId());
        return ApiResponse.success(planOrderService.assignRole(planOrderAssignRoleDTO));
    }

    @GetMapping("/detail")
    public ApiResponse<PlanOrderDetailResponse> detail(@RequestParam Long planOrderId, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        planOrderPermissionGuard.checkView(context, planOrderId);
        return ApiResponse.success(planOrderService.getDetail(planOrderId));
    }

    @PostMapping("/send-service-form")
    public ApiResponse<WecomTouchLog> sendServiceForm(@RequestBody PlanOrderSendServiceFormDTO request,
                                                      HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        Long planOrderId = request == null ? null : request.getPlanOrderId();
        planOrderPermissionGuard.checkUpdate(context, planOrderId);
        return ApiResponse.success(planOrderService.sendServiceForm(planOrderId, request == null ? null : request.getMessage()));
    }
}
