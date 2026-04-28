package com.seedcrm.crm.auth.controller;

import com.seedcrm.crm.auth.dto.SystemAccessMenuResponse;
import com.seedcrm.crm.auth.dto.SystemAccessMenuSaveRequest;
import com.seedcrm.crm.auth.dto.SystemAccessRoleResponse;
import com.seedcrm.crm.auth.dto.SystemAccessRoleSaveRequest;
import com.seedcrm.crm.auth.dto.SystemAccessSnapshotResponse;
import com.seedcrm.crm.auth.service.SystemAccessConfigService;
import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionModuleGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system-access")
public class SystemAccessConfigController {

    private final SystemAccessConfigService systemAccessConfigService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final PermissionModuleGuard permissionModuleGuard;

    public SystemAccessConfigController(SystemAccessConfigService systemAccessConfigService,
                                        PermissionRequestContextResolver permissionRequestContextResolver,
                                        PermissionModuleGuard permissionModuleGuard) {
        this.systemAccessConfigService = systemAccessConfigService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.permissionModuleGuard = permissionModuleGuard;
    }

    @GetMapping("/snapshot")
    public ApiResponse<SystemAccessSnapshotResponse> snapshot(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        permissionModuleGuard.checkView(context);
        return ApiResponse.success(systemAccessConfigService.snapshot());
    }

    @PostMapping("/menu/save")
    public ApiResponse<SystemAccessMenuResponse> saveMenu(@RequestBody SystemAccessMenuSaveRequest requestBody,
                                                          HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        permissionModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemAccessConfigService.saveMenu(requestBody));
    }

    @PostMapping("/role/save")
    public ApiResponse<SystemAccessRoleResponse> saveRole(@RequestBody SystemAccessRoleSaveRequest requestBody,
                                                          HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        permissionModuleGuard.checkUpdate(context);
        return ApiResponse.success(systemAccessConfigService.saveRole(requestBody));
    }
}
