package com.seedcrm.crm.wecom.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.WecomModuleGuard;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.wecom.dto.WecomLiveCodeGenerateRequest;
import com.seedcrm.crm.wecom.dto.WecomLiveCodeGenerateResponse;
import com.seedcrm.crm.wecom.dto.WecomLiveCodePublishRequest;
import com.seedcrm.crm.wecom.dto.WecomSendRequest;
import com.seedcrm.crm.wecom.entity.WecomAppConfig;
import com.seedcrm.crm.wecom.entity.WecomLiveCodeConfig;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.entity.WecomTouchRule;
import com.seedcrm.crm.wecom.service.WecomConsoleService;
import com.seedcrm.crm.wecom.service.WecomTouchService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wecom")
public class WecomController {

    private final WecomTouchService wecomTouchService;
    private final WecomConsoleService wecomConsoleService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final WecomModuleGuard wecomModuleGuard;

    public WecomController(WecomTouchService wecomTouchService,
                           WecomConsoleService wecomConsoleService,
                           PermissionRequestContextResolver permissionRequestContextResolver,
                           WecomModuleGuard wecomModuleGuard) {
        this.wecomTouchService = wecomTouchService;
        this.wecomConsoleService = wecomConsoleService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.wecomModuleGuard = wecomModuleGuard;
    }

    @GetMapping("/config")
    public ApiResponse<WecomAppConfig> getConfig(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        wecomModuleGuard.checkView(context);
        return ApiResponse.success(wecomConsoleService.getConfig());
    }

    @PostMapping("/config/save")
    public ApiResponse<WecomAppConfig> saveConfig(@RequestBody WecomAppConfig request,
                                                  HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        wecomModuleGuard.checkConfigManage(context);
        return ApiResponse.success(wecomConsoleService.saveConfig(request));
    }

    @PostMapping("/config/test")
    public ApiResponse<WecomAppConfig> testConfig(@RequestBody WecomAppConfig request,
                                                  HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        wecomModuleGuard.checkConfigManage(context);
        return ApiResponse.success(wecomConsoleService.testConfig(request));
    }

    @GetMapping("/rules")
    public ApiResponse<List<WecomTouchRule>> listRules(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        wecomModuleGuard.checkConfigManage(context);
        return ApiResponse.success(wecomConsoleService.listRules());
    }

    @PostMapping("/rule/save")
    public ApiResponse<WecomTouchRule> saveRule(@RequestBody WecomTouchRule request,
                                                HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        wecomModuleGuard.checkConfigManage(context);
        return ApiResponse.success(wecomConsoleService.saveRule(request));
    }

    @PostMapping("/rule/toggle")
    public ApiResponse<WecomTouchRule> toggleRule(@RequestParam Long ruleId,
                                                  HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        wecomModuleGuard.checkConfigManage(context);
        return ApiResponse.success(wecomConsoleService.toggleRule(ruleId));
    }

    @GetMapping("/logs")
    public ApiResponse<List<WecomTouchLog>> listLogs(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        wecomModuleGuard.checkView(context);
        return ApiResponse.success(wecomConsoleService.listLogs());
    }

    @GetMapping("/callback/logs")
    public ApiResponse<List<IntegrationCallbackEventLog>> listCallbackLogs(@RequestParam(required = false) String appCode,
                                                                           HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        wecomModuleGuard.checkView(context);
        return ApiResponse.success(wecomConsoleService.listCallbackLogs(appCode));
    }

    @GetMapping("/live-code/configs")
    public ApiResponse<List<WecomLiveCodeConfig>> listLiveCodeConfigs(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        wecomModuleGuard.checkView(context);
        return ApiResponse.success(wecomConsoleService.listLiveCodeConfigs());
    }

    @PostMapping("/live-code/config/save")
    public ApiResponse<WecomLiveCodeConfig> saveLiveCodeConfig(@RequestBody WecomLiveCodeConfig request,
                                                               HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        wecomModuleGuard.checkUpdate(context);
        return ApiResponse.success(wecomConsoleService.saveLiveCodeConfig(request));
    }

    @PostMapping("/send")
    public ApiResponse<WecomTouchLog> send(@RequestBody WecomSendRequest request,
                                           HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        wecomModuleGuard.checkUpdate(context);
        return ApiResponse.success(wecomTouchService.manualSend(
                request == null ? null : request.getCustomerId(),
                request == null ? null : request.getMessage()));
    }

    @PostMapping("/live-code/generate")
    public ApiResponse<WecomLiveCodeGenerateResponse> generateLiveCode(@RequestBody WecomLiveCodeGenerateRequest request,
                                                                       HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        wecomModuleGuard.checkUpdate(context);
        return ApiResponse.success(wecomTouchService.generateLiveCode(
                request == null ? null : request.getCodeName(),
                request == null ? null : request.getScene(),
                request == null ? null : request.getStrategy(),
                request == null ? null : request.getEmployeeNames(),
                request == null ? null : request.getEmployeeAccounts()));
    }

    @PostMapping("/live-code/publish")
    public ApiResponse<WecomLiveCodeConfig> publishLiveCode(@RequestBody WecomLiveCodePublishRequest request,
                                                            HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        wecomModuleGuard.checkUpdate(context);
        return ApiResponse.success(wecomConsoleService.publishLiveCodeConfig(
                request == null ? null : request.getConfigId(),
                request == null ? null : request.getStoreNames()));
    }

    @GetMapping(value = "/callback/{appCode}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receiveWecomCallbackVerify(@PathVariable String appCode,
                                             @RequestParam Map<String, String> parameters,
                                             HttpServletRequest request) {
        return wecomConsoleService.receiveCallback(appCode, request.getRequestURI(), request.getMethod(), parameters, null);
    }

    @PostMapping(value = "/callback/{appCode}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receiveWecomCallback(@PathVariable String appCode,
                                       @RequestParam Map<String, String> parameters,
                                       @RequestBody(required = false) String payload,
                                       HttpServletRequest request) {
        return wecomConsoleService.receiveCallback(appCode, request.getRequestURI(), request.getMethod(), parameters, payload);
    }
}
