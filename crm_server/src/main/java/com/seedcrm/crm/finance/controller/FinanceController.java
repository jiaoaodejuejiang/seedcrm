package com.seedcrm.crm.finance.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.finance.dto.FinanceBalanceResponse;
import com.seedcrm.crm.finance.dto.FinanceCheckResponse;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.permission.support.FinancePermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/finance")
public class FinanceController {

    private final FinanceService financeService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final FinancePermissionGuard financePermissionGuard;

    public FinanceController(FinanceService financeService,
                             PermissionRequestContextResolver permissionRequestContextResolver,
                             FinancePermissionGuard financePermissionGuard) {
        this.financeService = financeService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.financePermissionGuard = financePermissionGuard;
    }

    @GetMapping("/balance")
    public ApiResponse<FinanceBalanceResponse> balance(@RequestParam String ownerType,
                                                       @RequestParam(required = false) Long ownerId,
                                                       HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        financePermissionGuard.checkView(context);
        return ApiResponse.success(financeService.getBalance(AccountOwnerType.fromCode(ownerType), ownerId));
    }

    @PostMapping("/check")
    public ApiResponse<FinanceCheckResponse> check(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        financePermissionGuard.checkUpdate(context);
        return ApiResponse.success(financeService.check());
    }
}
