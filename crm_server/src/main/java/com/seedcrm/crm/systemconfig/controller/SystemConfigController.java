package com.seedcrm.crm.systemconfig.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SchedulerModuleGuard;
import com.seedcrm.crm.permission.support.SettingModuleGuard;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system-config")
@Tag(name = "系统配置", description = "系统基础域名、API 域名和受控配置项。域名配置用于生成回调、Swagger 和分销联调地址。")
public class SystemConfigController {

    private static final Set<String> DOMAIN_READ_ROLES = Set.of("INTEGRATION_ADMIN", "INTEGRATION_OPERATOR");
    private static final Set<String> INTEGRATION_CONFIG_WRITE_ROLES = Set.of("INTEGRATION_ADMIN");
    private static final Set<String> RUNTIME_CAPABILITY_READ_ROLES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");
    private static final String DISTRIBUTION_ORDER_TYPE_PREFIX = "distribution.order.type.";
    private static final String DEPOSIT_DIRECT_PREFIX = "deposit.direct.";

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
    @Operation(
            summary = "查询系统配置列表",
            description = "敏感配置值会按服务端规则脱敏返回。仅系统设置视图权限可访问。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<SystemConfigDtos.ConfigResponse>> list(@RequestParam(required = false) String prefix,
                                                                   @Parameter(hidden = true)
                                                                   HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        if (isDistributionIntegrationConfig(prefix) && isIntegrationReader(context)) {
            schedulerModuleGuard.checkView(context);
            return ApiResponse.success(systemConfigService.listConfigs(prefix));
        }
        if (isRuntimeCapabilityConfig(prefix) && isRuntimeCapabilityReader(context)) {
            return ApiResponse.success(systemConfigService.listConfigs(prefix));
        }
        settingModuleGuard.checkView(context);
        return ApiResponse.success(systemConfigService.listConfigs(prefix));
    }

    @GetMapping("/change-logs")
    @Operation(
            summary = "查询系统配置发布记录",
            description = "返回配置变更前后值、操作人、风险等级和影响模块；敏感值会脱敏展示。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<SystemConfigDtos.ChangeLogResponse>> changeLogs(@RequestParam(required = false) String prefix,
                                                                            @RequestParam(required = false) String configKey,
                                                                            @RequestParam(required = false) Integer limit,
                                                                            @Parameter(hidden = true)
                                                                            HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        if (isDistributionIntegrationConfig(firstText(configKey, prefix)) && isIntegrationReader(context)) {
            schedulerModuleGuard.checkView(context);
            return ApiResponse.success(systemConfigService.listChangeLogs(prefix, configKey, limit));
        }
        settingModuleGuard.checkView(context);
        return ApiResponse.success(systemConfigService.listChangeLogs(prefix, configKey, limit));
    }

    @GetMapping("/drafts")
    @Operation(
            summary = "查询系统配置草稿",
            description = "返回待发布配置草稿；敏感值会脱敏展示。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<List<SystemConfigDtos.DraftResponse>> drafts(@RequestParam(required = false) String status,
                                                                    @RequestParam(required = false) Integer limit,
                                                                    @Parameter(hidden = true)
                                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkView(context);
        return ApiResponse.success(systemConfigService.listDrafts(status, limit));
    }

    @GetMapping("/drafts/{draftNo}")
    @Operation(
            summary = "查询系统配置草稿详情",
            description = "返回草稿差异详情；敏感值会脱敏展示。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemConfigDtos.DraftResponse> draft(@PathVariable String draftNo,
                                                             @Parameter(hidden = true)
                                                             HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkView(context);
        return ApiResponse.success(systemConfigService.getDraft(draftNo));
    }

    @PostMapping("/drafts")
    @Operation(
            summary = "创建系统配置草稿",
            description = "只创建草稿，不改变运行中的配置。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemConfigDtos.DraftResponse> createDraft(@RequestBody SystemConfigDtos.SaveConfigRequest requestBody,
                                                                   HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemConfigService.createDraft(requestBody, context));
    }

    @PostMapping("/drafts/{draftNo}/publish")
    @Operation(
            summary = "发布系统配置草稿",
            description = "发布前会检查运行时配置是否已变化，确认无冲突后才写入生效配置。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemConfigDtos.DraftResponse> publishDraft(@PathVariable String draftNo,
                                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemConfigService.publishDraft(draftNo, context));
    }

    @PostMapping("/drafts/{draftNo}/discard")
    @Operation(
            summary = "作废系统配置草稿",
            description = "作废草稿，不改变运行中的配置。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemConfigDtos.DraftResponse> discardDraft(@PathVariable String draftNo,
                                                                    HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemConfigService.discardDraft(draftNo, context));
    }

    @PostMapping("/change-logs/{id}/rollback-preview")
    @Operation(
            summary = "生成系统配置回滚预览",
            description = "基于发布记录生成回滚差异预览，不保存任何配置。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemConfigDtos.ConfigPreviewResponse> rollbackPreview(@PathVariable("id") Long id,
                                                                               HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemConfigService.rollbackPreview(id));
    }

    @PostMapping("/change-logs/{id}/rollback-draft")
    @Operation(
            summary = "创建系统配置回滚草稿",
            description = "基于发布记录创建回滚草稿；只有发布草稿后运行配置才会变化。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemConfigDtos.DraftResponse> createRollbackDraft(@PathVariable("id") Long id,
                                                                          HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemConfigService.createRollbackDraft(id, context));
    }

    @PostMapping("/preview")
    @Operation(
            summary = "预览系统配置变更",
            description = "校验配置请求但不保存，返回差异、风险等级和影响模块。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemConfigDtos.ConfigPreviewResponse> preview(@RequestBody SystemConfigDtos.SaveConfigRequest requestBody,
                                                                       HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        if (requestBody != null
                && isDistributionIntegrationConfig(requestBody.getConfigKey())
                && isIntegrationWriter(context)) {
            schedulerModuleGuard.checkUpdate(context);
            return ApiResponse.success(systemConfigService.previewConfig(requestBody));
        }
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemConfigService.previewConfig(requestBody));
    }

    @GetMapping("/domain-settings")
    @Operation(
            summary = "读取系统基础域名和 API 域名",
            description = "集成管理员 / 集成操作员可通过调度查看权限读取；写入仍只允许系统设置更新权限。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemConfigDtos.DomainSettingsResponse> getDomainSettings(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        checkDomainSettingsView(context);
        return ApiResponse.success(systemConfigService.getDomainSettings());
    }

    @PostMapping("/domain-settings")
    @Operation(
            summary = "保存系统基础域名和 API 域名",
            description = "保存后会重新生成分销入站、Swagger/OpenAPI、支付/企微/抖音等配置页展示用地址，并写入配置变更审计。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemConfigDtos.DomainSettingsResponse> saveDomainSettings(@RequestBody SystemConfigDtos.SaveDomainSettingsRequest requestBody,
                                                                                  HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemConfigService.saveDomainSettings(requestBody, context));
    }

    @PostMapping("/save")
    @Operation(
            summary = "保存受控系统配置项",
            description = "仅允许白名单配置 key 写入，所有保存都会记录配置变更日志。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemConfigDtos.ConfigResponse> save(@RequestBody SystemConfigDtos.SaveConfigRequest requestBody,
                                                            HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        if (requestBody != null
                && isDistributionIntegrationConfig(requestBody.getConfigKey())
                && isIntegrationWriter(context)) {
            schedulerModuleGuard.checkUpdate(context);
            return ApiResponse.success(systemConfigService.saveConfig(requestBody, context));
        }
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

    private String firstText(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }

    private boolean isIntegrationReader(PermissionRequestContext context) {
        return DOMAIN_READ_ROLES.contains(normalize(context == null ? null : context.getRoleCode()));
    }

    private boolean isIntegrationWriter(PermissionRequestContext context) {
        return INTEGRATION_CONFIG_WRITE_ROLES.contains(normalize(context == null ? null : context.getRoleCode()));
    }

    private boolean isRuntimeCapabilityReader(PermissionRequestContext context) {
        return RUNTIME_CAPABILITY_READ_ROLES.contains(normalize(context == null ? null : context.getRoleCode()));
    }

    private boolean isRuntimeCapabilityConfig(String keyOrPrefix) {
        String value = keyOrPrefix == null ? "" : keyOrPrefix.trim();
        return value.startsWith(DEPOSIT_DIRECT_PREFIX) || value.equals("deposit.direct");
    }

    private boolean isDistributionIntegrationConfig(String keyOrPrefix) {
        String value = keyOrPrefix == null ? "" : keyOrPrefix.trim();
        return value.startsWith(DISTRIBUTION_ORDER_TYPE_PREFIX)
                || value.equals("distribution.order.type")
                || value.equals("distribution.order.type.mapping");
    }
}
