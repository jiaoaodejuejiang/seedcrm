package com.seedcrm.crm.workbench.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.support.OrderAmountMaskingSupport;
import com.seedcrm.crm.permission.support.CluePermissionGuard;
import com.seedcrm.crm.permission.support.CustomerPermissionGuard;
import com.seedcrm.crm.permission.support.OrderPermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.PlanOrderPermissionGuard;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workbench")
public class WorkbenchController {

    private static final String CONFIG_STORE_AMOUNT_HIDDEN = "amount.visibility.store_staff_hidden";
    private static final String CONFIG_STORE_AMOUNT_HIDDEN_ROLES = "amount.visibility.store_staff_hidden_roles";
    private static final String CONFIG_SERVICE_AMOUNT_HIDDEN_ROLES = "amount.visibility.service_confirm_hidden_roles";
    private static final String DOUYIN_CLUE_JOB_CODE = "DOUYIN_CLUE_INCREMENTAL";
    private static final String DEFAULT_STORE_AMOUNT_HIDDEN_ROLES =
            "STORE_SERVICE,STORE_MANAGER,PHOTOGRAPHER,MAKEUP_ARTIST,PHOTO_SELECTOR";
    private static final String DEFAULT_SERVICE_AMOUNT_HIDDEN_ROLES =
            "STORE_SERVICE,PHOTOGRAPHER,MAKEUP_ARTIST";
    private static final Set<String> STORE_AMOUNT_RESTRICTED_ROLES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");

    private final WorkbenchService workbenchService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final CluePermissionGuard cluePermissionGuard;
    private final CustomerPermissionGuard customerPermissionGuard;
    private final OrderPermissionGuard orderPermissionGuard;
    private final PlanOrderPermissionGuard planOrderPermissionGuard;
    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final SchedulerService schedulerService;
    private final SchedulerSensitiveDataMasker schedulerSensitiveDataMasker;

    public WorkbenchController(WorkbenchService workbenchService,
                               PermissionRequestContextResolver permissionRequestContextResolver,
                               CluePermissionGuard cluePermissionGuard,
                               CustomerPermissionGuard customerPermissionGuard,
                               OrderPermissionGuard orderPermissionGuard,
                               PlanOrderPermissionGuard planOrderPermissionGuard,
                               ObjectMapper objectMapper,
                               SystemConfigService systemConfigService,
                               SchedulerService schedulerService,
                               SchedulerSensitiveDataMasker schedulerSensitiveDataMasker) {
        this.workbenchService = workbenchService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.cluePermissionGuard = cluePermissionGuard;
        this.customerPermissionGuard = customerPermissionGuard;
        this.orderPermissionGuard = orderPermissionGuard;
        this.planOrderPermissionGuard = planOrderPermissionGuard;
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
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
                .peek(order -> maskOrderAmountsIfNeeded(order, context))
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
                .peek(planOrder -> maskPlanOrderAmountsIfNeeded(planOrder, context))
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
        maskPlanOrderWorkbenchAmountsIfNeeded(response, context);
        return ApiResponse.success(response);
    }

    private void maskPlanOrderWorkbenchAmountsIfNeeded(PlanOrderWorkbenchResponse response, PermissionRequestContext context) {
        if (response == null || (!shouldMaskBusinessAmounts(context) && !shouldMaskServiceAmounts(context))) {
            return;
        }
        maskPlanOrderAmountsIfNeeded(response.getSummary(), context);
        maskOrderAmountsIfNeeded(response.getOrder(), context);
    }

    private void maskPlanOrderAmountsIfNeeded(PlanOrderItemResponse response, PermissionRequestContext context) {
        if (response != null && shouldMaskBusinessAmounts(context)) {
            response.setAmount(null);
        }
    }

    private void maskOrderAmountsIfNeeded(OrderItemResponse response, PermissionRequestContext context) {
        if (response != null && shouldMaskBusinessAmounts(context)) {
            response.setAmount(null);
            response.setDeposit(null);
        }
        if (response != null && shouldMaskServiceAmounts(context)) {
            response.setServiceDetailJson(OrderAmountMaskingSupport.maskServiceDetailJson(
                    response.getServiceDetailJson(), objectMapper));
        }
    }

    private boolean shouldMaskBusinessAmounts(PermissionRequestContext context) {
        return context != null
                && systemConfigService.getBoolean(CONFIG_STORE_AMOUNT_HIDDEN, true)
                && context.getRoleCode() != null
                && configuredRoles(CONFIG_STORE_AMOUNT_HIDDEN_ROLES, DEFAULT_STORE_AMOUNT_HIDDEN_ROLES, STORE_AMOUNT_RESTRICTED_ROLES)
                .contains(context.getRoleCode().trim().toUpperCase(Locale.ROOT));
    }

    private boolean shouldMaskServiceAmounts(PermissionRequestContext context) {
        return context != null
                && systemConfigService.getBoolean(CONFIG_STORE_AMOUNT_HIDDEN, true)
                && context.getRoleCode() != null
                && configuredRoles(CONFIG_SERVICE_AMOUNT_HIDDEN_ROLES, DEFAULT_SERVICE_AMOUNT_HIDDEN_ROLES,
                Set.of("STORE_SERVICE", "PHOTOGRAPHER", "MAKEUP_ARTIST"))
                .contains(context.getRoleCode().trim().toUpperCase(Locale.ROOT));
    }

    private Set<String> configuredRoles(String key, String defaultRoles, Set<String> fallbackRoles) {
        String configuredRoles = systemConfigService.getString(key, defaultRoles);
        Set<String> roles = Arrays.stream(configuredRoles.split("[,，\\s]+"))
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isBlank())
                .collect(Collectors.toSet());
        return roles.isEmpty() ? fallbackRoles : roles;
    }

    @GetMapping("/customers/{customerId}")
    public ApiResponse<CustomerProfileResponse> customerDetail(@PathVariable Long customerId,
                                                               HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        customerPermissionGuard.checkView(context, customerId);
        CustomerProfileResponse response = workbenchService.getCustomerProfile(customerId);
        maskCustomerProfileAmountsIfNeeded(response, context);
        return ApiResponse.success(response);
    }

    private void maskCustomerProfileAmountsIfNeeded(CustomerProfileResponse response, PermissionRequestContext context) {
        if (response == null || response.getOrderHistory() == null
                || (!shouldMaskBusinessAmounts(context) && !shouldMaskServiceAmounts(context))) {
            return;
        }
        response.getOrderHistory().forEach(order -> maskOrderAmountsIfNeeded(order, context));
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
