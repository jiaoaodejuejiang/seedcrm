package com.seedcrm.crm.wecom.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.WecomModuleGuard;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.dto.SchedulerCallbackDebugRequest;
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
import java.util.LinkedHashMap;
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
import org.springframework.util.StringUtils;

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

    @GetMapping(value = "/mock-contact/{contactWayId}", produces = MediaType.TEXT_HTML_VALUE)
    public String mockContact(@PathVariable String contactWayId,
                              @RequestParam Map<String, String> parameters,
                              HttpServletRequest request) {
        WecomAppConfig config = wecomConsoleService.getConfig();
        if (config != null && "LIVE".equalsIgnoreCase(config.getExecutionMode())) {
            return renderMockContactPage("当前为企业微信真实模式，请使用企业微信返回的真实二维码。", false);
        }
        String state = parameters == null ? null : parameters.get("state");
        if (!StringUtils.hasText(state)) {
            return renderMockContactPage("MOCK 基础活码打开成功。本码仅验证门店发布，客户绑定请从订单列表生成带 state 的专属活码。", true);
        }

        String externalUserid = parameters == null ? null : parameters.get("external_userid");
        if (!StringUtils.hasText(externalUserid)) {
            externalUserid = "mock_ext_" + Integer.toUnsignedString((contactWayId + "|" + state).hashCode(), 36);
        }
        Map<String, String> callbackParameters = new LinkedHashMap<>();
        callbackParameters.put("state", state);
        callbackParameters.put("external_userid", externalUserid);
        callbackParameters.put("contact_way_id", contactWayId);
        String payload = "{\"event\":\"add_external_contact\",\"state\":\"" + escapeJson(state)
                + "\",\"external_userid\":\"" + escapeJson(externalUserid)
                + "\",\"contact_way_id\":\"" + escapeJson(contactWayId) + "\"}";
        try {
            wecomConsoleService.receiveCallback(
                    "PRIVATE_DOMAIN",
                    request == null ? "/wecom/mock-contact/" + contactWayId : request.getRequestURI(),
                    "POST",
                    callbackParameters,
                    payload);
            return renderMockContactPage("MOCK 加好友成功，系统已按 state 完成客户企微关系绑定。", true);
        } catch (Exception exception) {
            return renderMockContactPage("MOCK 加好友失败：" + exception.getMessage(), false);
        }
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

    @PostMapping("/callback/debug")
    public ApiResponse<Map<String, Object>> debugWecomCallback(@RequestBody SchedulerCallbackDebugRequest request,
                                                               HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        wecomModuleGuard.checkConfigManage(context);
        String appCode = request == null || request.getCallbackName() == null
                ? "PRIVATE_DOMAIN"
                : request.getCallbackName();
        String callbackPath = request == null || request.getCallbackPath() == null
                ? "/wecom/callback/" + appCode
                : request.getCallbackPath();
        String requestMethod = request == null || request.getRequestMethod() == null
                ? "POST"
                : request.getRequestMethod();
        Map<String, String> parameters = request == null || request.getParameters() == null
                ? Map.of("debug", "true")
                : request.getParameters();
        String result = wecomConsoleService.receiveCallback(appCode, callbackPath, requestMethod, parameters, request == null ? null : request.getPayload());
        List<IntegrationCallbackEventLog> logs = wecomConsoleService.listCallbackLogs(appCode);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "企业微信回调联调已完成，已写入回调记录");
        response.put("callbackResult", result);
        response.put("latestLog", logs.isEmpty() ? null : logs.get(0));
        return ApiResponse.success(response);
    }

    private String renderMockContactPage(String message, boolean success) {
        String color = success ? "#0f766e" : "#b45309";
        String title = success ? "企微 MOCK 联调成功" : "企微 MOCK 联调提示";
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width,initial-scale=1" />
                  <title>%s</title>
                  <style>
                    body { margin:0; min-height:100vh; display:grid; place-items:center; background:#f6f3ee; font-family: "Microsoft YaHei", sans-serif; color:#173042; }
                    .card { width:min(92vw, 440px); padding:32px; border-radius:24px; background:white; box-shadow:0 24px 60px rgba(23,48,66,.16); text-align:center; }
                    .badge { display:inline-flex; padding:8px 14px; border-radius:999px; color:white; background:%s; font-size:13px; }
                    h1 { margin:18px 0 12px; font-size:24px; }
                    p { margin:0; line-height:1.7; color:#64748b; }
                  </style>
                </head>
                <body>
                  <main class="card">
                    <span class="badge">SeedCRM MOCK</span>
                    <h1>%s</h1>
                    <p>%s</p>
                  </main>
                </body>
                </html>
                """.formatted(title, color, title, escapeHtml(message));
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
