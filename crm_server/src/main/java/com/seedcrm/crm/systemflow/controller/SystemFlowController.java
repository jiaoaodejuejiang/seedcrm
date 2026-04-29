package com.seedcrm.crm.systemflow.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SettingModuleGuard;
import com.seedcrm.crm.systemflow.dto.SystemFlowDtos;
import com.seedcrm.crm.systemflow.service.SystemFlowService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system-flow")
public class SystemFlowController {

    private final SystemFlowService systemFlowService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final SettingModuleGuard settingModuleGuard;

    public SystemFlowController(SystemFlowService systemFlowService,
                                PermissionRequestContextResolver permissionRequestContextResolver,
                                SettingModuleGuard settingModuleGuard) {
        this.systemFlowService = systemFlowService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.settingModuleGuard = settingModuleGuard;
    }

    @GetMapping("/list")
    public ApiResponse<List<SystemFlowDtos.DefinitionResponse>> list(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowView(context);
        return ApiResponse.success(systemFlowService.listDefinitions());
    }

    @GetMapping("/versions")
    public ApiResponse<List<SystemFlowDtos.VersionResponse>> versions(@RequestParam(required = false) String flowCode,
                                                                      HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowView(context);
        return ApiResponse.success(systemFlowService.listVersions(flowCode));
    }

    @GetMapping("/detail")
    public ApiResponse<SystemFlowDtos.DetailResponse> detail(@RequestParam(required = false) String flowCode,
                                                             @RequestParam(required = false) Long versionId,
                                                             HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowView(context);
        return ApiResponse.success(systemFlowService.detail(flowCode, versionId));
    }

    @PostMapping("/save-draft")
    public ApiResponse<SystemFlowDtos.DetailResponse> saveDraft(@RequestBody SystemFlowDtos.SaveDraftRequest requestBody,
                                                                HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowDraft(context);
        return ApiResponse.success(systemFlowService.saveDraft(requestBody, context));
    }

    @PostMapping("/preview-diff")
    public ApiResponse<SystemFlowDtos.DiffPreviewResponse> previewDiff(@RequestBody SystemFlowDtos.SaveDraftRequest requestBody,
                                                                       HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowDraft(context);
        return ApiResponse.success(systemFlowService.previewDiff(requestBody));
    }

    @GetMapping("/validation-report")
    public ApiResponse<SystemFlowDtos.ValidationReportResponse> validationReport(@RequestParam(required = false) String flowCode,
                                                                                 @RequestParam(required = false) Long versionId,
                                                                                 HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowView(context);
        return ApiResponse.success(systemFlowService.validationReport(flowCode, versionId));
    }

    @GetMapping("/trigger-linkage-report")
    public ApiResponse<SystemFlowDtos.TriggerLinkageReportResponse> triggerLinkageReport(@RequestParam(required = false) String flowCode,
                                                                                         @RequestParam(required = false) Long versionId,
                                                                                         HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowView(context);
        return ApiResponse.success(systemFlowService.triggerLinkageReport(flowCode, versionId));
    }

    @PostMapping("/publish")
    public ApiResponse<SystemFlowDtos.DetailResponse> publish(@RequestBody SystemFlowDtos.PublishRequest requestBody,
                                                              HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowPublish(context);
        return ApiResponse.success(systemFlowService.publish(requestBody, context));
    }

    @PostMapping("/disable")
    public ApiResponse<SystemFlowDtos.DefinitionResponse> disable(@RequestBody SystemFlowDtos.DisableRequest requestBody,
                                                                  HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowPublish(context);
        return ApiResponse.success(systemFlowService.disable(requestBody, context));
    }

    @PostMapping("/simulate")
    public ApiResponse<SystemFlowDtos.SimulateResponse> simulate(@RequestBody SystemFlowDtos.SimulateRequest requestBody,
                                                                 HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowDebug(context);
        return ApiResponse.success(systemFlowService.simulate(requestBody));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<SystemFlowDtos.AuditLogResponse>> auditLogs(@RequestParam(required = false) String flowCode,
                                                                        HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkSystemFlowView(context);
        return ApiResponse.success(systemFlowService.listAuditLogs(flowCode));
    }
}
