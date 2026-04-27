package com.seedcrm.crm.scheduler.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SchedulerModuleGuard;
import com.seedcrm.crm.scheduler.dto.SchedulerJobUpsertRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerTriggerRequest;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackConfig;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.entity.SchedulerJob;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import com.seedcrm.crm.scheduler.service.SchedulerService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final SchedulerModuleGuard schedulerModuleGuard;

    public SchedulerController(SchedulerService schedulerService,
                               SchedulerIntegrationService schedulerIntegrationService,
                               PermissionRequestContextResolver permissionRequestContextResolver,
                               SchedulerModuleGuard schedulerModuleGuard) {
        this.schedulerService = schedulerService;
        this.schedulerIntegrationService = schedulerIntegrationService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.schedulerModuleGuard = schedulerModuleGuard;
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
        schedulerModuleGuard.checkTrigger(context);
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
        return ApiResponse.success(schedulerIntegrationService.listCallbackLogs(providerCode));
    }

    @PostMapping("/callback/save")
    public ApiResponse<IntegrationCallbackConfig> saveCallback(@RequestBody IntegrationCallbackConfig request,
                                                               HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkUpdate(context);
        return ApiResponse.success(schedulerIntegrationService.saveCallback(request));
    }

    @PostMapping("/job/save")
    public ApiResponse<SchedulerJob> saveJob(@RequestBody SchedulerJobUpsertRequest request,
                                             HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkUpdate(context);
        return ApiResponse.success(schedulerService.saveJob(request));
    }

    @PostMapping("/trigger")
    public ApiResponse<SchedulerJobLog> trigger(@RequestBody SchedulerTriggerRequest request,
                                                HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(schedulerService.trigger(request));
    }

    @PostMapping("/retry")
    public ApiResponse<List<SchedulerJobLog>> retry(@RequestParam String jobCode,
                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        schedulerModuleGuard.checkTrigger(context);
        return ApiResponse.success(schedulerService.retryFailed(jobCode));
    }

    @GetMapping(value = "/oauth/douyin/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receiveDouyinOauthCallback(@RequestParam Map<String, String> parameters,
                                             HttpServletRequest request) {
        schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客授权回调",
                request.getRequestURI(),
                request.getMethod(),
                parameters,
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
                parameters,
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
                parameters,
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
                parameters,
                payload);
        return "success";
    }
}
