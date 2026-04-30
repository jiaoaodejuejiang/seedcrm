package com.seedcrm.crm.scheduler.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SchedulerModuleGuard;
import com.seedcrm.crm.scheduler.dto.SchedulerJobUpsertRequest;
import com.seedcrm.crm.scheduler.dto.DistributionReconciliationDtos.DistributionReconciliationResult;
import com.seedcrm.crm.scheduler.dto.SchedulerCallbackDebugRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerInterfaceDebugRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerQueueActionRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerTriggerRequest;
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
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import com.seedcrm.crm.scheduler.service.SchedulerOutboxService;
import com.seedcrm.crm.scheduler.service.SchedulerService;
import com.seedcrm.crm.scheduler.service.impl.DistributionEventDryRunService;
import com.seedcrm.crm.scheduler.support.SchedulerSensitiveDataMasker;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scheduler")
public class SchedulerController {

    private final SchedulerService schedulerService;
    private final SchedulerIntegrationService schedulerIntegrationService;
    private final DistributionEventDryRunService distributionEventDryRunService;
    private final SchedulerOutboxService schedulerOutboxService;
    private final DistributionExceptionService distributionExceptionService;
    private final DistributionExceptionRetryService distributionExceptionRetryService;
    private final DistributionReconciliationService distributionReconciliationService;
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
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.schedulerModuleGuard = schedulerModuleGuard;
        this.schedulerSensitiveDataMasker = schedulerSensitiveDataMasker;
    }

    @GetMapping("/jobs")
    public ApiResponse<List<SchedulerJob>> listJobs(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(schedulerService.listJobs());
    }

    @GetMapping("/logs")
    public ApiResponse<List<SchedulerJobLog>> listLogs(@RequestParam(required = false) String jobCode,
                                                       HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(schedulerService.listLogs(jobCode));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<SchedulerJobAuditLog>> listAuditLogs(@RequestParam(required = false) String jobCode,
                                                                 HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(schedulerService.listAuditLogs(jobCode));
    }

    @GetMapping("/outbox/events")
    public ApiResponse<List<SchedulerOutboxEvent>> listOutboxEvents(@RequestParam(required = false) String status,
                                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(schedulerOutboxService.list(status));
    }

    @PostMapping("/outbox/retry")
    public ApiResponse<SchedulerOutboxEvent> retryOutboxEvent(@RequestBody(required = false) SchedulerQueueActionRequest request,
                                                              HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(schedulerOutboxService.retry(actionId(request), context));
    }

    @PostMapping("/outbox/process")
    public ApiResponse<List<SchedulerOutboxEvent>> processOutboxEvents(@RequestParam(required = false) Integer limit,
                                                                       HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(schedulerOutboxService.processDue(limit == null ? 20 : limit));
    }

    @GetMapping("/distribution/exceptions")
    public ApiResponse<List<DistributionExceptionRecord>> listDistributionExceptions(@RequestParam(required = false) String status,
                                                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(schedulerSensitiveDataMasker.maskDistributionExceptions(
                distributionExceptionService.list(status),
                context));
    }

    @PostMapping("/distribution/exceptions/retry")
    public ApiResponse<DistributionExceptionRecord> retryDistributionException(@RequestBody(required = false) SchedulerQueueActionRequest request,
                                                                              HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(distributionExceptionService.retry(actionId(request), context, actionRemark(request)));
    }

    @PostMapping("/distribution/exceptions/handled")
    public ApiResponse<DistributionExceptionRecord> markDistributionExceptionHandled(@RequestBody(required = false) SchedulerQueueActionRequest request,
                                                                                   HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkUpdate(context);
        return ApiResponse.success(distributionExceptionService.markHandled(actionId(request), context, actionRemark(request)));
    }

    @PostMapping("/distribution/exceptions/process")
    public ApiResponse<List<DistributionExceptionRecord>> processDistributionExceptionRetries(@RequestParam(required = false) Integer limit,
                                                                                             HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(distributionExceptionRetryService.processRetryQueue(limit == null ? 10 : limit));
    }

    @PostMapping("/distribution/status-check/process")
    public ApiResponse<List<DistributionReconciliationResult>> processDistributionStatusCheck(@RequestParam(required = false) Integer limit,
                                                                                              HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(distributionReconciliationService.checkOrderStatus(limit == null ? 20 : limit));
    }

    @PostMapping("/distribution/reconcile/process")
    public ApiResponse<List<DistributionReconciliationResult>> processDistributionReconciliation(@RequestParam(required = false) Integer limit,
                                                                                                HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(distributionReconciliationService.pullReconciliation(limit == null ? 20 : limit));
    }

    @GetMapping("/providers")
    public ApiResponse<List<IntegrationProviderConfig>> listProviders(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(schedulerIntegrationService.listProviders());
    }

    @PostMapping("/provider/save")
    public ApiResponse<IntegrationProviderConfig> saveProvider(@RequestBody IntegrationProviderConfig request,
                                                               HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkUpdate(context);
        return ApiResponse.success(schedulerIntegrationService.saveProvider(request));
    }

    @PostMapping("/provider/test")
    public ApiResponse<IntegrationProviderConfig> testProvider(@RequestBody IntegrationProviderConfig request,
                                                               HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkDebug(context);
        return ApiResponse.success(schedulerIntegrationService.testProvider(request));
    }

    @GetMapping("/callbacks")
    public ApiResponse<List<IntegrationCallbackConfig>> listCallbacks(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(schedulerIntegrationService.listCallbacks());
    }

    @GetMapping("/callback/logs")
    public ApiResponse<List<IntegrationCallbackEventLog>> listCallbackLogs(@RequestParam(required = false) String providerCode,
                                                                           HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkView(context);
        return ApiResponse.success(schedulerSensitiveDataMasker.maskCallbackLogs(
                schedulerIntegrationService.listCallbackLogs(providerCode),
                context));
    }

    @PostMapping("/callback/save")
    public ApiResponse<IntegrationCallbackConfig> saveCallback(@RequestBody IntegrationCallbackConfig request,
                                                               HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkUpdate(context);
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
        response.put("provider", provider);
        response.put("latestLog", logs.isEmpty() ? null : logs.get(0));
        return ApiResponse.success(response);
    }

    @PostMapping("/job/save")
    public ApiResponse<SchedulerJob> saveJob(@RequestBody SchedulerJobUpsertRequest request,
                                             HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkUpdate(context);
        return ApiResponse.success(schedulerService.saveJob(request, context));
    }

    @PostMapping("/trigger")
    public ApiResponse<SchedulerJobLog> trigger(@RequestBody SchedulerTriggerRequest request,
                                                HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(schedulerService.trigger(request, context));
    }

    @PostMapping("/retry")
    public ApiResponse<List<SchedulerJobLog>> retry(@RequestParam String jobCode,
                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(schedulerService.retryFailed(jobCode, context));
    }

    @PostMapping("/retry-log")
    public ApiResponse<SchedulerJobLog> retryLog(@RequestParam Long logId,
                                                 HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(schedulerService.retryLog(logId, context));
    }

    @PostMapping("/interface/debug")
    public ApiResponse<Map<String, Object>> debugInterface(@RequestBody SchedulerInterfaceDebugRequest request,
                                                           HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkDebug(context);
        String mode = request == null || request.getMode() == null ? "MOCK" : request.getMode().trim().toUpperCase();
        String providerCode = request == null || request.getProviderCode() == null
                ? "DOUYIN_LAIKE"
                : request.getProviderCode().trim().toUpperCase();
        String interfaceCode = request == null || request.getInterfaceCode() == null
                ? "DOUYIN_CLUE_PULL"
                : request.getInterfaceCode().trim().toUpperCase();
        if ("DISTRIBUTION".equals(providerCode) || interfaceCode.startsWith("DISTRIBUTION_")) {
            return ApiResponse.success(distributionEventDryRunService.dryRun(request));
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
            response.put("provider", provider);
            return ApiResponse.success(response);
        }
        response.put("success", true);
        response.put("message", "模拟接口调试成功，未调用外部平台");
        response.put("mockData", Map.of(
                "traceId", java.util.UUID.randomUUID().toString(),
                "received", true,
                "nextAction", "可在任务调度中触发同步，或切换真实模式校验配置"));
        return ApiResponse.success(response);
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
}
