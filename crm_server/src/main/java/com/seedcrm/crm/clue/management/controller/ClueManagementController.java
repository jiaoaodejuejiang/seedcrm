package com.seedcrm.crm.clue.management.controller;

import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.AssignmentStrategyRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.AssignmentStrategyResponse;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DedupConfigRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DedupConfigResponse;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DutyCustomerServiceBatchRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DutyCustomerServiceResponse;
import com.seedcrm.crm.clue.management.service.ClueManagementService;
import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.ClueManagementGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clue-management")
public class ClueManagementController {

    private final ClueManagementService clueManagementService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final ClueManagementGuard clueManagementGuard;

    public ClueManagementController(ClueManagementService clueManagementService,
                                    PermissionRequestContextResolver permissionRequestContextResolver,
                                    ClueManagementGuard clueManagementGuard) {
        this.clueManagementService = clueManagementService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.clueManagementGuard = clueManagementGuard;
    }

    @GetMapping("/assignment-strategy")
    public ApiResponse<AssignmentStrategyResponse> assignmentStrategy(HttpServletRequest request) {
        PermissionRequestContext context = resolveContext(request);
        return ApiResponse.success(clueManagementService.getAssignmentStrategy(context.getCurrentStoreId()));
    }

    @PostMapping("/assignment-strategy")
    public ApiResponse<AssignmentStrategyResponse> saveAssignmentStrategy(@RequestBody(required = false) AssignmentStrategyRequest request,
                                                                         HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = resolveContext(httpServletRequest);
        return ApiResponse.success(clueManagementService.saveAssignmentStrategy(
                context.getCurrentStoreId(),
                context.getCurrentUserId(),
                request));
    }

    @GetMapping("/dedup-config")
    public ApiResponse<DedupConfigResponse> dedupConfig(HttpServletRequest request) {
        resolveContext(request);
        return ApiResponse.success(clueManagementService.getDedupConfig());
    }

    @PostMapping("/dedup-config")
    public ApiResponse<DedupConfigResponse> saveDedupConfig(@RequestBody(required = false) DedupConfigRequest request,
                                                            HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = resolveContext(httpServletRequest);
        return ApiResponse.success(clueManagementService.saveDedupConfig(request, context));
    }

    @GetMapping("/duty-cs")
    public ApiResponse<List<DutyCustomerServiceResponse>> dutyCustomerServices(HttpServletRequest request) {
        PermissionRequestContext context = resolveContext(request);
        return ApiResponse.success(clueManagementService.listDutyCustomerServices(context.getCurrentStoreId()));
    }

    @PostMapping("/duty-cs")
    public ApiResponse<List<DutyCustomerServiceResponse>> saveDutyCustomerServices(
            @RequestBody(required = false) DutyCustomerServiceBatchRequest request,
            HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = resolveContext(httpServletRequest);
        return ApiResponse.success(clueManagementService.saveDutyCustomerServices(context.getCurrentStoreId(), request));
    }

    private PermissionRequestContext resolveContext(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        clueManagementGuard.checkManage(context);
        return context;
    }
}
