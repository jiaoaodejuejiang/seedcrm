package com.seedcrm.crm.permission.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.entity.PermissionPolicy;
import com.seedcrm.crm.permission.service.PermissionService;
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

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/policies")
    public ApiResponse<List<PermissionPolicy>> listPolicies() {
        return ApiResponse.success(permissionService.listPolicies());
    }

    @PostMapping("/policy/save")
    public ApiResponse<PermissionPolicy> savePolicy(@RequestBody PermissionPolicy policy) {
        return ApiResponse.success(permissionService.savePolicy(policy));
    }

    @PostMapping("/check")
    public ApiResponse<PermissionCheckResponse> check(@RequestBody PermissionCheckRequest request) {
        return ApiResponse.success(permissionService.check(request));
    }
}
