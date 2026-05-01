package com.seedcrm.crm.scheduler.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SchedulerModuleGuard;
import com.seedcrm.crm.scheduler.dto.SchedulerJobUpsertRequest;
import com.seedcrm.crm.scheduler.dto.DistributionReconciliationDtos.DistributionReconciliationResult;
import com.seedcrm.crm.scheduler.dto.SchedulerCallbackDebugRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerGoLiveReadinessResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerInterfaceDebugRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerQueueActionRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerTriggerRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerMonitorSummaryResponse;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackConfig;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.entity.SchedulerJob;
import com.seedcrm.crm.scheduler.entity.SchedulerJobAuditLog;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import com.seedcrm.crm.scheduler.service.DistributionExceptionService;
import com.seedcrm.crm.scheduler.service.DistributionExceptionRetryService;
import com.seedcrm.crm.scheduler.service.DistributionReconciliationService;
import com.seedcrm.crm.scheduler.service.SchedulerIdempotencyHealthService;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import com.seedcrm.crm.scheduler.service.SchedulerGoLiveReadinessService;
import com.seedcrm.crm.scheduler.service.SchedulerMonitorService;
import com.seedcrm.crm.scheduler.service.SchedulerOutboxService;
import com.seedcrm.crm.scheduler.service.SchedulerService;
import com.seedcrm.crm.scheduler.service.impl.DistributionEventDryRunService;
import com.seedcrm.crm.scheduler.support.SchedulerSensitiveDataMasker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scheduler")
@Tag(name = "调度中心后台 API", description = "调度配置、联调工作台、Outbox 回推、异常队列、状态回查和对账。Distribution 调度不得绕过统一入站服务直接写核心表。")
public class SchedulerController {

    private static final Set<String> GLOBAL_DISTRIBUTION_PROCESS_ROLES = Set.of("ADMIN", "INTEGRATION_ADMIN");

    private final SchedulerService schedulerService;
    private final SchedulerIntegrationService schedulerIntegrationService;
    private final DistributionEventDryRunService distributionEventDryRunService;
    private final SchedulerOutboxService schedulerOutboxService;
    private final DistributionExceptionService distributionExceptionService;
    private final DistributionExceptionRetryService distributionExceptionRetryService;
    private final DistributionReconciliationService distributionReconciliationService;
    private final SchedulerIdempotencyHealthService schedulerIdempotencyHealthService;
    private final SchedulerGoLiveReadinessService schedulerGoLiveReadinessService;
    private final SchedulerMonitorService schedulerMonitorService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final SchedulerModuleGuard schedulerModuleGuard;
    private final SchedulerSensitiveDataMasker schedulerSensitiveDataMasker;

    public SchedulerController(SchedulerService schedulerService,
                               SchedulerIntegrationService schedulerIntegrationService,
                               DistributionEventDryRunService distributionEventDryRunService,
                               SchedulerOutboxService schedulerOutboxService,
                               DistributionExceptionService distributionExceptionService,
                               DistributionExceptionRetryService distributionExceptionRetryService,
                               DistributionReconciliationService distributionReconciliationService,
                               SchedulerIdempotencyHealthService schedulerIdempotencyHealthService,
                               SchedulerGoLiveReadinessService schedulerGoLiveReadinessService,
                               SchedulerMonitorService schedulerMonitorService,
                               PermissionRequestContextResolver permissionRequestContextResolver,
                               SchedulerModuleGuard schedulerModuleGuard,
                               SchedulerSensitiveDataMasker schedulerSensitiveDataMasker) {
        this.schedulerService = schedulerService;
        this.schedulerIntegrationService = schedulerIntegrationService;
        this.distributionEventDryRunService = distributionEventDryRunService;
        this.schedulerOutboxService = schedulerOutboxService;
        this.distributionExceptionService = distributionExceptionService;
        this.distributionExceptionRetryService = distributionExceptionRetryService;
        this.distributionReconciliationService = distributionReconciliationService;
        this.schedulerIdempotencyHealthService = schedulerIdempotencyHealthService;
        this.schedulerGoLiveReadinessService = schedulerGoLiveReadinessService;
        this.schedulerMonitorService = schedulerMonitorService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.schedulerModuleGuard = schedulerModuleGuard;
        this.schedulerSensitiveDataMasker = schedulerSensitiveDataMasker;
    }

    @GetMapping("/jobs")
    public ApiResponse<List<SchedulerJob>> listJobs(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        rejectPartnerScopedGlobalView(context);
        return ApiResponse.success(schedulerService.listJobs());
    }

    @GetMapping("/logs")
    public ApiResponse<List<SchedulerJobLog>> listLogs(@RequestParam(required = false) String jobCode,
                                                       HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        rejectPartnerScopedGlobalView(context);
        return ApiResponse.success(schedulerSensitiveDataMasker.maskJobLogs(
                schedulerService.listLogs(jobCode),
                context));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<SchedulerJobAuditLog>> listAuditLogs(@RequestParam(required = false) String jobCode,
                                                                 HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        rejectPartnerScopedGlobalView(context);
        return ApiResponse.success(schedulerSensitiveDataMasker.maskAuditLogs(
                schedulerService.listAuditLogs(jobCode),
                context));
    }

    @GetMapping("/outbox/events")
    @Operation(
            summary = "查询分销履约回推 Outbox",
            description = "PlanOrder finished 后的 crm.order.used 回推事件在此查看；失败不会回滚本地履约事务。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<SchedulerOutboxEvent>> listOutboxEvents(@RequestParam(required = false) String status,
                                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(schedulerSensitiveDataMasker.maskOutboxEvents(
                filterPartnerOutboxEvents(schedulerOutboxService.list(status), context),
                context));
    }

    @PostMapping("/outbox/retry")
    @Operation(
            summary = "重试单条 Outbox 回推事件",
            description = "仅重置可重试状态，不直接修改 Customer / Order / PlanOrder。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SchedulerOutboxEvent> retryOutboxEvent(@RequestBody(required = false) SchedulerQueueActionRequest request,
                                                              HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(schedulerSensitiveDataMasker.maskOutboxEvent(
                schedulerOutboxService.retry(actionId(request), context),
                context));
    }

    @PostMapping("/outbox/process")
    @Operation(
            summary = "处理到期 Outbox 回推队列",
            description = "仅管理员或集成管理员可触发全局批处理；LIVE 模式按配置签名回推外部分销系统。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<SchedulerOutboxEvent>> processOutboxEvents(@RequestParam(required = false) Integer limit,
                                                                        HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        rejectPartnerScopedGlobalMutation(context);
        rejectNonAdminGlobalDistributionProcess(context);
        return ApiResponse.success(schedulerSensitiveDataMasker.maskOutboxEvents(
                schedulerOutboxService.processDue(limit == null ? 20 : limit),
                context));
    }

    @GetMapping("/distribution/exceptions")
    @Operation(
            summary = "查询分销异常队列",
            description = "入站冲突、字段缺失、幂等冲突等异常在此追踪和处理；异常队列不得绕过入站服务写核心表。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<DistributionExceptionRecord>> listDistributionExceptions(@RequestParam(required = false) String status,
                                                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(schedulerSensitiveDataMasker.maskDistributionExceptions(
                filterPartnerDistributionExceptions(distributionExceptionService.list(status), context),
                context));
    }

    @PostMapping("/distribution/exceptions/retry")
    @Operation(
            summary = "重放分销异常事件",
            description = "由 Scheduler 以可信重放方式调用 DistributionEventIngestService.replayFromScheduler；仅管理员或集成管理员可执行。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<DistributionExceptionRecord> retryDistributionException(@RequestBody(required = false) SchedulerQueueActionRequest request,
                                                                              HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkTrigger(context);
        rejectNonAdminCoreReplay(context);
        return ApiResponse.success(distributionExceptionService.retry(actionId(request), context, actionRemark(request)));
    }

    @PostMapping("/distribution/exceptions/handled")
    @Operation(
            summary = "标记分销异常为已处理",
            description = "用于人工治理后关闭异常记录，不直接修正核心业务数据。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<DistributionExceptionRecord> markDistributionExceptionHandled(@RequestBody(required = false) SchedulerQueueActionRequest request,
                                                                                   HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkUpdate(context);
        return ApiResponse.success(distributionExceptionService.markHandled(actionId(request), context, actionRemark(request)));
    }

    @PostMapping("/distribution/exceptions/process")
    @Operation(
            summary = "处理分销异常重试队列",
            description = "批量处理 RETRY_QUEUED 异常，成功后标记 HANDLED，失败回到 OPEN。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<DistributionExceptionRecord>> processDistributionExceptionRetries(@RequestParam(required = false) Integer limit,
                                                                                              HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        rejectPartnerScopedGlobalMutation(context);
        rejectNonAdminGlobalDistributionProcess(context);
        return ApiResponse.success(distributionExceptionRetryService.processRetryQueue(limit == null ? 10 : limit));
    }

    @PostMapping("/distribution/status-check/process")
    @Operation(
            summary = "执行分销订单状态回查",
            description = "回查结果如需改变订单状态，必须生成 distribution.order.* 事件并经统一入站服务重放。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<DistributionReconciliationResult>> processDistributionStatusCheck(@RequestParam(required = false) Integer limit,
                                                                                               HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        rejectPartnerScopedGlobalMutation(context);
        rejectNonAdminGlobalDistributionProcess(context);
        return ApiResponse.success(distributionReconciliationService.checkOrderStatus(limit == null ? 20 : limit));
    }

    @PostMapping("/distribution/status-check/dry-run")
    @Operation(
            summary = "分销订单状态回查预检",
            description = "只预览本地订单、接口路径和可能产生的入站事件；不调用外部接口，不重放事件，不写 Customer / Order / PlanOrder。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<DistributionReconciliationResult>> dryRunDistributionStatusCheck(@RequestParam(required = false) Integer limit,
                                                                                              HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkDebug(context);
        rejectPartnerScopedGlobalMutation(context);
        return ApiResponse.success(distributionReconciliationService.dryRunOrderStatus(limit == null ? 20 : limit));
    }

    @PostMapping("/distribution/reconcile/process")
    @Operation(
            summary = "执行分销对账拉取",
            description = "对账拉取只读取外部或 MOCK 数据；需要创建或更新订单时必须转成入站事件处理。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<DistributionReconciliationResult>> processDistributionReconciliation(@RequestParam(required = false) Integer limit,
                                                                                                 HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        rejectPartnerScopedGlobalMutation(context);
        rejectNonAdminGlobalDistributionProcess(context);
        return ApiResponse.success(distributionReconciliationService.pullReconciliation(limit == null ? 20 : limit));
    }

    @PostMapping("/distribution/reconcile/dry-run")
    @Operation(
            summary = "分销对账拉取预检",
            description = "只校验配置与本地样本；LIVE 模式不调用外部分销系统，MOCK 模式不重放事件，不写核心业务表。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<DistributionReconciliationResult>> dryRunDistributionReconciliation(@RequestParam(required = false) Integer limit,
                                                                                                HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkDebug(context);
        rejectPartnerScopedGlobalMutation(context);
        return ApiResponse.success(distributionReconciliationService.dryRunReconciliation(limit == null ? 20 : limit));
    }

    @GetMapping("/idempotency-health")
    @Operation(
            summary = "检查接口幂等健康",
            description = "用于上线前发现重复回调日志、唯一约束缺失等数据治理风险。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SchedulerIdempotencyHealthResponse> inspectIdempotencyHealth(@RequestParam(required = false) String providerCode,
                                                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        rejectPartnerScopedHealthView(context);
        return ApiResponse.success(schedulerSensitiveDataMasker.maskIdempotencyHealth(
                schedulerIdempotencyHealthService.inspect(providerCode),
                context));
    }

    @GetMapping("/monitor/summary")
    @Operation(
            summary = "查询调度与分销验收监控摘要",
            description = "提供方案 B 上线验收样本、Outbox、异常队列和回查对账概览。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SchedulerMonitorSummaryResponse> inspectMonitorSummary(@RequestParam(required = false) String providerCode,
                                                                              HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        rejectPartnerScopedHealthView(context);
        return ApiResponse.success(schedulerMonitorService.summarize(providerCode));
    }

    @GetMapping("/go-live/readiness")
    @Operation(
            summary = "上线前检查",
            description = "检查域名、回调地址、分销 LIVE 配置、幂等唯一约束、生产 OpenAPI 与调度任务是否具备上线条件。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SchedulerGoLiveReadinessResponse> inspectGoLiveReadiness(@RequestParam(required = false) String providerCode,
                                                                                HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        rejectPartnerScopedHealthView(context);
        return ApiResponse.success(schedulerGoLiveReadinessService.inspect(providerCode));
    }

    @GetMapping("/providers")
    public ApiResponse<List<IntegrationProviderConfig>> listProviders(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(filterPartnerProviders(schedulerIntegrationService.listProviders(), context));
    }

    @PostMapping("/provider/save")
    public ApiResponse<IntegrationProviderConfig> saveProvider(@RequestBody IntegrationProviderConfig request,
                                                               HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkUpdate(context);
        rejectPartnerScopedGlobalMutation(context);
        return ApiResponse.success(schedulerIntegrationService.saveProvider(request));
    }

    @PostMapping("/provider/test")
    public ApiResponse<IntegrationProviderConfig> testProvider(@RequestBody IntegrationProviderConfig request,
                                                               HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkDebug(context);
        rejectPartnerScopedProviderMismatch(request == null ? null : request.getProviderCode(), context);
        return ApiResponse.success(schedulerIntegrationService.testProvider(request));
    }

    @GetMapping("/callbacks")
    public ApiResponse<List<IntegrationCallbackConfig>> listCallbacks(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(filterPartnerCallbacks(schedulerIntegrationService.listCallbacks(), context));
    }

    @GetMapping("/callback/logs")
    public ApiResponse<List<IntegrationCallbackEventLog>> listCallbackLogs(@RequestParam(required = false) String providerCode,
                                                                           HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        String scopedProviderCode = isPartnerScoped(context) ? context.getCurrentPartnerCode() : providerCode;
        return ApiResponse.success(schedulerSensitiveDataMasker.maskCallbackLogs(
                filterPartnerCallbackLogs(schedulerIntegrationService.listCallbackLogs(scopedProviderCode), context),
                context));
    }

    @PostMapping("/callback/save")
    public ApiResponse<IntegrationCallbackConfig> saveCallback(@RequestBody IntegrationCallbackConfig request,
                                                               HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkUpdate(context);
        rejectPartnerScopedGlobalMutation(context);
        return ApiResponse.success(schedulerIntegrationService.saveCallback(request));
    }

    @PostMapping("/callback/debug")
    public ApiResponse<Map<String, Object>> debugCallback(@RequestBody SchedulerCallbackDebugRequest request,
                                                          HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkDebug(context);
        String providerCode = request == null || request.getProviderCode() == null
                ? "DOUYIN_LAIKE"
                : request.getProviderCode();
        rejectPartnerScopedProviderMismatch(providerCode, context);
        String callbackName = request == null || request.getCallbackName() == null
                ? "回调联调"
                : request.getCallbackName();
        String callbackPath = request == null || request.getCallbackPath() == null
                ? "/scheduler/callback/debug"
                : request.getCallbackPath();
        String requestMethod = request == null || request.getRequestMethod() == null
                ? "POST"
                : request.getRequestMethod();
        Map<String, String> parameters = request == null || request.getParameters() == null
                ? Map.of("debug", "true")
                : request.getParameters();
        String payload = request == null ? null : request.getPayload();
        IntegrationProviderConfig provider = schedulerIntegrationService.receiveProviderCallback(
                providerCode,
                callbackName,
                callbackPath,
                requestMethod,
                withRequestMetadata(parameters, httpServletRequest),
                payload);
        List<IntegrationCallbackEventLog> logs = schedulerIntegrationService.listCallbackLogs(providerCode);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "回调联调已完成，已写入回调记录");
        response.put("provider", safeProviderPreview(provider));
        response.put("latestLog", logs.isEmpty() ? null : schedulerSensitiveDataMasker.maskCallbackLogs(
                List.of(logs.get(0)),
                context).get(0));
        return ApiResponse.success(response);
    }

    @PostMapping("/job/save")
    public ApiResponse<SchedulerJob> saveJob(@RequestBody SchedulerJobUpsertRequest request,
                                              HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkUpdate(context);
        rejectPartnerScopedGlobalMutation(context);
        return ApiResponse.success(schedulerService.saveJob(request, context));
    }

    @PostMapping("/trigger")
    public ApiResponse<SchedulerJobLog> trigger(@RequestBody SchedulerTriggerRequest request,
                                                 HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkTrigger(context);
        rejectPartnerScopedGlobalMutation(context);
        return ApiResponse.success(schedulerService.trigger(request, context));
    }

    @PostMapping("/trigger/dry-run")
    @Operation(
            summary = "调度任务预检",
            description = "预览任务执行影响，不入队、不调用外部接口、不写 Customer / Order / PlanOrder / Outbox。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SchedulerJobLog> dryRunTrigger(@RequestBody SchedulerTriggerRequest request,
                                                       HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkDebug(context);
        rejectPartnerScopedGlobalMutation(context);
        return ApiResponse.success(schedulerService.dryRun(request, context));
    }

    @PostMapping("/retry")
    public ApiResponse<List<SchedulerJobLog>> retry(@RequestParam String jobCode,
                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        rejectPartnerScopedGlobalMutation(context);
        return ApiResponse.success(schedulerService.retryFailed(jobCode, context));
    }

    @PostMapping("/retry-log")
    public ApiResponse<SchedulerJobLog> retryLog(@RequestParam Long logId,
                                                  HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        rejectPartnerScopedGlobalMutation(context);
        return ApiResponse.success(schedulerService.retryLog(logId, context));
    }

    @PostMapping("/interface/debug")
    @Operation(
            summary = "联调工作台 dry-run",
            description = "分销接口 dry-run 只预览字段映射、状态映射、Customer 匹配和 Order 幂等结果，不写 Customer / Order / PlanOrder。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<Map<String, Object>> debugInterface(@RequestBody SchedulerInterfaceDebugRequest request,
                                                           HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkDebug(context);
        String mode = request == null || request.getMode() == null ? "MOCK" : request.getMode().trim().toUpperCase();
        String providerCode = request == null || request.getProviderCode() == null
                ? "DOUYIN_LAIKE"
                : request.getProviderCode().trim().toUpperCase();
        rejectPartnerScopedProviderMismatch(providerCode, context);
        String interfaceCode = request == null || request.getInterfaceCode() == null
                ? "DOUYIN_CLUE_PULL"
                : request.getInterfaceCode().trim().toUpperCase();
        if ("DISTRIBUTION".equals(providerCode) || interfaceCode.startsWith("DISTRIBUTION_")) {
            return ApiResponse.success(schedulerSensitiveDataMasker.maskDryRunResult(
                    distributionEventDryRunService.dryRun(request),
                    context));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mode", mode);
        response.put("providerCode", providerCode);
        response.put("interfaceCode", interfaceCode);
        response.put("requestMethod", request == null ? "POST" : request.getRequestMethod());
        response.put("path", request == null ? null : request.getPath());
        response.put("parameters", request == null ? Map.of() : request.getParameters());
        response.put("payload", request == null ? null : request.getPayload());
        if ("LIVE".equals(mode)) {
            IntegrationProviderConfig provider = schedulerIntegrationService.listProviders().stream()
                    .filter(item -> providerCode.equalsIgnoreCase(item.getProviderCode()))
                    .findFirst()
                    .orElse(null);
            response.put("success", provider != null);
            response.put("message", provider == null
                    ? "未找到对应接口配置，请先在抖音接口或分销接口中保存配置"
                    : "真实模式已完成配置校验；实际业务调用仍由调度任务或业务动作触发");
            response.put("provider", safeProviderPreview(provider));
            return ApiResponse.success(schedulerSensitiveDataMasker.maskDryRunResult(response, context));
        }
        response.put("success", true);
        response.put("message", "模拟联调预检成功，未调用外部平台");
        response.put("mockData", Map.of(
                "traceId", java.util.UUID.randomUUID().toString(),
                "received", true,
                "nextAction", "可在任务调度中触发同步，或切换真实模式校验配置"));
        return ApiResponse.success(schedulerSensitiveDataMasker.maskDryRunResult(response, context));
    }

    @GetMapping(value = "/oauth/douyin/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receiveDouyinOauthCallback(@RequestParam Map<String, String> parameters,
                                             HttpServletRequest request) {
        schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客授权回调",
                request.getRequestURI(),
                request.getMethod(),
                withRequestMetadata(parameters, request),
                null);
        return "success";
    }

    @PostMapping(value = "/oauth/douyin/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receiveDouyinOauthCallbackPost(@RequestParam Map<String, String> parameters,
                                                 @RequestBody(required = false) String payload,
                                                 HttpServletRequest request) {
        schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客授权回调",
                request.getRequestURI(),
                request.getMethod(),
                withRequestMetadata(parameters, request),
                payload);
        return "success";
    }

    @PostMapping(value = "/callback/douyin/refund", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receiveDouyinRefundCallback(@RequestParam Map<String, String> parameters,
                                              @RequestBody(required = false) String payload,
                                              HttpServletRequest request) {
        schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客退款回调",
                request.getRequestURI(),
                request.getMethod(),
                withRequestMetadata(parameters, request),
                payload);
        return "success";
    }

    @PostMapping(value = "/callback/douyin/refund-audit", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receiveDouyinRefundAuditCallback(@RequestParam Map<String, String> parameters,
                                                   @RequestBody(required = false) String payload,
                                                   HttpServletRequest request) {
        schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客退款审核回调",
                request.getRequestURI(),
                request.getMethod(),
                withRequestMetadata(parameters, request),
                payload);
        return "success";
    }

    private Long actionId(SchedulerQueueActionRequest request) {
        return request == null ? null : request.getId();
    }

    private String actionRemark(SchedulerQueueActionRequest request) {
        return request == null ? null : request.getRemark();
    }

    private Map<String, String> withRequestMetadata(Map<String, String> parameters, HttpServletRequest request) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (parameters != null) {
            merged.putAll(parameters);
        }
        if (request == null) {
            return merged;
        }
        putIfPresent(merged, "__remote_ip", firstNonBlank(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()));
        putIfPresent(merged, "__user_agent", request.getHeader("User-Agent"));
        putIfPresent(merged, "__header_x_signature", request.getHeader("X-Signature"));
        putIfPresent(merged, "__header_x_seedcrm_signature", request.getHeader("X-Seedcrm-Signature"));
        putIfPresent(merged, "__header_x_douyin_signature", request.getHeader("X-Douyin-Signature"));
        putIfPresent(merged, "__header_x_timestamp", firstNonBlank(
                request.getHeader("X-Timestamp"),
                request.getHeader("X-Seedcrm-Timestamp")));
        putIfPresent(merged, "__header_x_nonce", firstNonBlank(
                request.getHeader("X-Nonce"),
                request.getHeader("X-Seedcrm-Nonce")));
        putIfPresent(merged, "__header_authorization", request.getHeader("Authorization"));
        return merged;
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void rejectPartnerScopedGlobalView(PermissionRequestContext context) {
        if (isPartnerScoped(context)) {
            throw new BusinessException("partner app cannot access global scheduler data");
        }
    }

    private void rejectPartnerScopedGlobalMutation(PermissionRequestContext context) {
        if (isPartnerScoped(context)) {
            throw new BusinessException("partner app cannot trigger global scheduler operations");
        }
    }

    private void rejectNonAdminGlobalDistributionProcess(PermissionRequestContext context) {
        String roleCode = context == null ? null : context.getRoleCode();
        if (roleCode == null || !GLOBAL_DISTRIBUTION_PROCESS_ROLES.contains(roleCode.trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException("current role cannot trigger global distribution queue processing");
        }
    }

    private void rejectNonAdminCoreReplay(PermissionRequestContext context) {
        String roleCode = context == null ? null : context.getRoleCode();
        if (roleCode == null || !GLOBAL_DISTRIBUTION_PROCESS_ROLES.contains(roleCode.trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException("current role cannot replay distribution exceptions");
        }
    }

    private void rejectPartnerScopedHealthView(PermissionRequestContext context) {
        if (isPartnerScoped(context)) {
            throw new BusinessException("partner app cannot inspect scheduler idempotency health");
        }
    }

    private void rejectPartnerScopedProviderMismatch(String providerCode,
                                                     PermissionRequestContext context) {
        if (isPartnerScoped(context) && !samePartner(context.getCurrentPartnerCode(), providerCode)) {
            throw new BusinessException("partner app cannot access another partner scheduler data");
        }
    }

    private Map<String, Object> safeProviderPreview(IntegrationProviderConfig provider) {
        if (provider == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerCode", provider.getProviderCode());
        result.put("providerName", provider.getProviderName());
        result.put("enabled", provider.getEnabled());
        result.put("executionMode", provider.getExecutionMode());
        result.put("endpointPath", provider.getEndpointPath());
        result.put("statusQueryPath", provider.getStatusQueryPath());
        result.put("reconciliationPullPath", provider.getReconciliationPullPath());
        result.put("secretConfigured", provider.getClientSecret() != null && !provider.getClientSecret().isBlank());
        return result;
    }

    private boolean isPartnerScoped(PermissionRequestContext context) {
        return context != null
                && "PARTNER".equalsIgnoreCase(context.getDataScope())
                && context.getCurrentPartnerCode() != null
                && !context.getCurrentPartnerCode().isBlank();
    }

    private List<SchedulerOutboxEvent> filterPartnerOutboxEvents(List<SchedulerOutboxEvent> events,
                                                                 PermissionRequestContext context) {
        if (!isPartnerScoped(context) || events == null) {
            return events;
        }
        String partnerCode = context.getCurrentPartnerCode();
        return events.stream()
                .filter(event -> samePartner(partnerCode, event.getProviderCode())
                        || samePartner(partnerCode, event.getExternalPartnerCode()))
                .toList();
    }

    private List<DistributionExceptionRecord> filterPartnerDistributionExceptions(List<DistributionExceptionRecord> records,
                                                                                 PermissionRequestContext context) {
        if (!isPartnerScoped(context) || records == null) {
            return records;
        }
        String partnerCode = context.getCurrentPartnerCode();
        return records.stream()
                .filter(record -> samePartner(partnerCode, record.getPartnerCode()))
                .toList();
    }

    private List<IntegrationProviderConfig> filterPartnerProviders(List<IntegrationProviderConfig> providers,
                                                                  PermissionRequestContext context) {
        if (!isPartnerScoped(context) || providers == null) {
            return providers;
        }
        String partnerCode = context.getCurrentPartnerCode();
        return providers.stream()
                .filter(provider -> samePartner(partnerCode, provider.getProviderCode()))
                .toList();
    }

    private List<IntegrationCallbackConfig> filterPartnerCallbacks(List<IntegrationCallbackConfig> callbacks,
                                                                   PermissionRequestContext context) {
        if (!isPartnerScoped(context) || callbacks == null) {
            return callbacks;
        }
        String partnerCode = context.getCurrentPartnerCode();
        return callbacks.stream()
                .filter(callback -> samePartner(partnerCode, callback.getProviderCode()))
                .toList();
    }

    private List<IntegrationCallbackEventLog> filterPartnerCallbackLogs(List<IntegrationCallbackEventLog> logs,
                                                                        PermissionRequestContext context) {
        if (!isPartnerScoped(context) || logs == null) {
            return logs;
        }
        String partnerCode = context.getCurrentPartnerCode();
        return logs.stream()
                .filter(log -> samePartner(partnerCode, log.getProviderCode()))
                .toList();
    }

    private boolean samePartner(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }
}
