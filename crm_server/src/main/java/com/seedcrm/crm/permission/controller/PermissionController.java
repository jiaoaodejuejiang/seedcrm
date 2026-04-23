package com.seedcrm.crm.permission.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.entity.PermissionPolicy;
import com.seedcrm.crm.permission.support.PermissionModuleGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permission")
public class PermissionController {

    private final PermissionService permissionService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final PermissionModuleGuard permissionModuleGuard;

    public PermissionController(PermissionService permissionService,
                                PermissionRequestContextResolver permissionRequestContextResolver,
                                PermissionModuleGuard permissionModuleGuard) {
        this.permissionService = permissionService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.permissionModuleGuard = permissionModuleGuard;
    }

    @GetMapping("/policies")
    public ApiResponse<List<PermissionPolicy>> listPolicies(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        permissionModuleGuard.checkView(context);
        return ApiResponse.success(permissionService.listPolicies());
    }

    @PostMapping("/policy/save")
    public ApiResponse<PermissionPolicy> savePolicy(@RequestBody PermissionPolicy policy, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        permissionModuleGuard.checkUpdate(context);
        return ApiResponse.success(permissionService.savePolicy(policy));
    }

    @PostMapping("/check")
    public ApiResponse<PermissionCheckResponse> check(@RequestBody PermissionCheckRequest requestBody,
                                                      HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        permissionModuleGuard.checkCheck(context);
        return ApiResponse.success(permissionService.check(requestBody));
    }
}
