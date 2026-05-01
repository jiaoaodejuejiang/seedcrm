package com.seedcrm.crm.systemconfig.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SettingModuleGuard;
import com.seedcrm.crm.systemconfig.dto.SystemGoLiveDtos;
import com.seedcrm.crm.systemconfig.service.SystemGoLiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system-config/go-live")
@Tag(name = "上线工具", description = "上线前检查、环境初始化和测试数据清理")
public class SystemGoLiveController {

    private final SystemGoLiveService systemGoLiveService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final SettingModuleGuard settingModuleGuard;

    public SystemGoLiveController(SystemGoLiveService systemGoLiveService,
                                  PermissionRequestContextResolver permissionRequestContextResolver,
                                  SettingModuleGuard settingModuleGuard) {
        this.systemGoLiveService = systemGoLiveService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.settingModuleGuard = settingModuleGuard;
    }

    @GetMapping("/summary")
    @Operation(
            summary = "读取上线准备摘要",
            description = "返回域名、运行环境、调度/分销接入检查项和可清理数据量。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemGoLiveDtos.SummaryResponse> summary(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkView(context);
        return ApiResponse.success(systemGoLiveService.summary());
    }

    @PostMapping("/initialize")
    @Operation(
            summary = "一键初始化上线配置",
            description = "保存环境模式、域名配置，可选择把集成切回 MOCK 或停用调度任务。PROD 初始化需要 INIT_PROD_SYSTEM 确认词。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemGoLiveDtos.OperationResponse> initialize(@RequestBody SystemGoLiveDtos.InitializeRequest requestBody,
                                                                     HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemGoLiveService.initialize(requestBody, context));
    }

    @PostMapping("/clear-test-data")
    @Operation(
            summary = "一键清理测试数据",
            description = "仅允许 LOCAL/DEV/TEST/STAGING 环境执行，必须输入 CLEAR_TEST_DATA；dry-run 只预览不删除。",
            security = @SecurityRequirement(name = "BackendToken"))
    public ApiResponse<SystemGoLiveDtos.OperationResponse> clearTestData(@RequestBody SystemGoLiveDtos.ClearTestDataRequest requestBody,
                                                                        HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        settingModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemGoLiveService.clearTestData(requestBody, context));
    }
}
