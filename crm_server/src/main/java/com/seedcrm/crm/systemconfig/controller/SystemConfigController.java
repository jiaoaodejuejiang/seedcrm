package com.seedcrm.crm.systemconfig.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SchedulerModuleGuard;
import com.seedcrm.crm.permission.support.SettingModuleGuard;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system-config")
public class SystemConfigController {

    private static final Set<String> DOMAIN_READ_ROLES = Set.of("INTEGRATION_ADMIN", "INTEGRATION_OPERATOR");

    private final SystemConfigService systemConfigService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final SettingModuleGuard settingModuleGuard;
    private final SchedulerModuleGuard schedulerModuleGuard;

    public SystemConfigController(SystemConfigService systemConfigService,
                                  PermissionRequestContextResolver permissionRequestContextResolver,
                                  SettingModuleGuard settingModuleGuard,
                                  SchedulerModuleGuard schedulerModuleGuard) {
        this.systemConfigService = systemConfigService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.settingModuleGuard = settingModuleGuard;
        this.schedulerModuleGuard = schedulerModuleGuard;
    }

    @GetMapping("/list")
    public ApiResponse<List<SystemConfigDtos.ConfigResponse>> list(@RequestParam(required = false) String prefix,
                                                                   HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkView(context);
        return ApiResponse.success(systemConfigService.listConfigs(prefix));
    }

    @GetMapping("/domain-settings")
    public ApiResponse<SystemConfigDtos.DomainSettingsResponse> getDomainSettings(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        checkDomainSettingsView(context);
        return ApiResponse.success(systemConfigService.getDomainSettings());
    }

    @PostMapping("/domain-settings")
    public ApiResponse<SystemConfigDtos.DomainSettingsResponse> saveDomainSettings(@RequestBody SystemConfigDtos.SaveDomainSettingsRequest requestBody,
                                                                                  HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemConfigService.saveDomainSettings(requestBody, context));
    }

    @PostMapping("/save")
    public ApiResponse<SystemConfigDtos.ConfigResponse> save(@RequestBody SystemConfigDtos.SaveConfigRequest requestBody,
                                                            HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemConfigService.saveConfig(requestBody, context));
    }

    private void checkDomainSettingsView(PermissionRequestContext context) {
        String roleCode = normalize(context == null ? null : context.getRoleCode());
        if (DOMAIN_READ_ROLES.contains(roleCode)) {
            schedulerModuleGuard.checkView(context);
            return;
        }
        settingModuleGuard.checkView(context);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
