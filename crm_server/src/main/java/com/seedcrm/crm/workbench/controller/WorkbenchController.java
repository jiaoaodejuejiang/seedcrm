package com.seedcrm.crm.workbench.controller;

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
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.ClueItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CluePageResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.ClueSyncStatusResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CustomerProfileResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.DistributorBoardItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.FinanceOverviewResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.OrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderWorkbenchResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StaffRoleOptionResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StoreLiveCodePreviewResponse;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.service.SchedulerService;
import com.seedcrm.crm.scheduler.support.SchedulerSensitiveDataMasker;
import com.seedcrm.crm.workbench.service.WorkbenchService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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

    private static final String DOUYIN_CLUE_JOB_CODE = "DOUYIN_CLUE_INCREMENTAL";

    private final WorkbenchService workbenchService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final CluePermissionGuard cluePermissionGuard;
    private final CustomerPermissionGuard customerPermissionGuard;
    private final OrderPermissionGuard orderPermissionGuard;
    private final PlanOrderPermissionGuard planOrderPermissionGuard;
    private final FinancePermissionGuard financePermissionGuard;
    private final SensitiveDataProjectionService sensitiveDataProjectionService;
    private final SchedulerService schedulerService;
    private final SchedulerSensitiveDataMasker schedulerSensitiveDataMasker;

    public WorkbenchController(WorkbenchService workbenchService,
                               PermissionRequestContextResolver permissionRequestContextResolver,
                               CluePermissionGuard cluePermissionGuard,
                               CustomerPermissionGuard customerPermissionGuard,
                               OrderPermissionGuard orderPermissionGuard,
                               PlanOrderPermissionGuard planOrderPermissionGuard,
                               FinancePermissionGuard financePermissionGuard,
                               SensitiveDataProjectionService sensitiveDataProjectionService,
                               SchedulerService schedulerService,
                               SchedulerSensitiveDataMasker schedulerSensitiveDataMasker) {
        this.workbenchService = workbenchService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.cluePermissionGuard = cluePermissionGuard;
        this.customerPermissionGuard = customerPermissionGuard;
        this.orderPermissionGuard = orderPermissionGuard;
        this.planOrderPermissionGuard = planOrderPermissionGuard;
        this.financePermissionGuard = financePermissionGuard;
        this.sensitiveDataProjectionService = sensitiveDataProjectionService;
        this.schedulerService = schedulerService;
        this.schedulerSensitiveDataMasker = schedulerSensitiveDataMasker;
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

    @GetMapping("/clues/page")
    public ApiResponse<CluePageResponse> cluePage(@RequestParam(required = false) String sourceChannel,
                                                  @RequestParam(required = false) String productSourceType,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String phone,
                                                  @RequestParam(required = false) String createdStart,
                                                  @RequestParam(required = false) String createdEnd,
                                                  @RequestParam(required = false) String queueStatus,
                                                  @RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "30") Integer pageSize,
                                                  HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        CluePageResponse response = workbenchService.pageClues(
                sourceChannel,
                productSourceType,
                status,
                phone,
                parseStartOfDay(createdStart),
                parseEndOfDay(createdEnd),
                queueStatus,
                page == null ? 1 : page,
                pageSize == null ? 30 : pageSize,
                clueId -> cluePermissionGuard.canView(context, clueId));
        return ApiResponse.success(response);
    }

    @GetMapping("/clues/sync-status")
    public ApiResponse<ClueSyncStatusResponse> clueSyncStatus(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        List<SchedulerJobLog> logs = schedulerService.listLogs(DOUYIN_CLUE_JOB_CODE);
        SchedulerJobLog latest = logs.isEmpty() ? null : logs.get(0);
        if (latest != null) {
            List<SchedulerJobLog> maskedLogs = schedulerSensitiveDataMasker.maskJobLogs(List.of(latest), context);
            latest = maskedLogs.isEmpty() ? latest : maskedLogs.get(0);
        }
        return ApiResponse.success(buildClueSyncStatus(latest));
    }

    private LocalDateTime parseStartOfDay(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime parseEndOfDay(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.plusDays(1).atStartOfDay().minusSeconds(1);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException("invalid date parameter: " + value);
        }
    }

    private ClueSyncStatusResponse buildClueSyncStatus(SchedulerJobLog log) {
        if (log == null) {
            return null;
        }
        return new ClueSyncStatusResponse(
                log.getId(),
                log.getJobCode(),
                log.getStatus(),
                log.getTriggerType(),
                log.getImportedCount(),
                log.getPayload(),
                log.getErrorMessage(),
                log.getDurationMs(),
                log.getStartedAt(),
                log.getFinishedAt(),
                log.getCreatedAt());
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
                .peek(order -> sensitiveDataProjectionService.projectOrderItem(order, context))
                .collect(Collectors.toList());
        return ApiResponse.success(orders);
    }

    @GetMapping("/orders/{orderId}/wecom-live-code")
    public ApiResponse<StoreLiveCodePreviewResponse> orderWecomLiveCode(@PathVariable Long orderId,
                                                                        HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkView(context, orderId);
        return ApiResponse.success(workbenchService.getOrderLiveCodePreview(orderId, context));
    }

    @GetMapping("/plan-orders")
    public ApiResponse<List<PlanOrderItemResponse>> planOrders(@RequestParam(required = false) String status,
                                                               HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        List<PlanOrderItemResponse> planOrders = workbenchService.listPlanOrders(status).stream()
                .filter(planOrder -> planOrderPermissionGuard.canView(context, planOrder.getPlanOrderId()))
                .peek(planOrder -> sensitiveDataProjectionService.projectPlanOrderItem(planOrder, context))
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
        PlanOrderWorkbenchResponse response = workbenchService.getPlanOrderWorkbench(planOrderId);
        return ApiResponse.success(sensitiveDataProjectionService.projectPlanOrderWorkbench(response, context));
    }

    @GetMapping("/customers/{customerId}")
    public ApiResponse<CustomerProfileResponse> customerDetail(@PathVariable Long customerId,
                                                               HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        customerPermissionGuard.checkView(context, customerId);
        CustomerProfileResponse response = workbenchService.getCustomerProfile(customerId);
        return ApiResponse.success(sensitiveDataProjectionService.projectCustomerProfile(response, context));
    }

    @GetMapping("/distributors")
    public ApiResponse<List<DistributorBoardItemResponse>> distributors(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        financePermissionGuard.checkView(context);
        return ApiResponse.success(workbenchService.listDistributors());
    }

    @GetMapping("/finance-overview")
    public ApiResponse<FinanceOverviewResponse> financeOverview(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        financePermissionGuard.checkView(context);
        return ApiResponse.success(workbenchService.getFinanceOverview());
    }

    @GetMapping("/staff-options")
    public ApiResponse<List<StaffRoleOptionResponse>> staffOptions() {
        return ApiResponse.success(workbenchService.listStaffOptions());
    }
}
