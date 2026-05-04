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
import java.util.Locale;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permission")
public class PermissionController {

    private static final Set<String> STORE_ROLE_MATRIX_READER_ROLES = Set.of("STORE_MANAGER");
    private static final Set<String> STORE_ROLE_MATRIX_ROLE_CODES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");
    private static final Set<String> STORE_ROLE_MATRIX_MODULE_CODES = Set.of("ORDER", "PLANORDER");
    private static final Set<String> STORE_ROLE_MATRIX_ACTION_CODES = Set.of(
            "VIEW",
            "UPDATE",
            "FINISH",
            "REFUND_STORE",
            "ASSIGN_ROLE");

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
        if (isStoreRoleMatrixReader(context)) {
            return ApiResponse.success(permissionService.listPolicies().stream()
                    .filter(this::isStoreRoleMatrixPolicy)
                    .toList());
        }
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

    private boolean isStoreRoleMatrixReader(PermissionRequestContext context) {
        return STORE_ROLE_MATRIX_READER_ROLES.contains(normalize(context == null ? null : context.getRoleCode()));
    }

    private boolean isStoreRoleMatrixPolicy(PermissionPolicy policy) {
        return policy != null
                && STORE_ROLE_MATRIX_ROLE_CODES.contains(normalize(policy.getRoleCode()))
                && STORE_ROLE_MATRIX_MODULE_CODES.contains(normalize(policy.getModuleCode()))
                && STORE_ROLE_MATRIX_ACTION_CODES.contains(normalize(policy.getActionCode()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
