package com.seedcrm.crm.salary.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SalaryModuleGuard;
import com.seedcrm.crm.salary.dto.SalaryBalanceResponse;
import com.seedcrm.crm.salary.dto.SalaryRecalculateRequest;
import com.seedcrm.crm.salary.dto.SalarySettlementCreateRequest;
import com.seedcrm.crm.salary.dto.SalarySettlementPolicyDtos;
import com.seedcrm.crm.salary.dto.SalarySettlementStatusRequest;
import com.seedcrm.crm.salary.dto.SalaryStatResponse;
import com.seedcrm.crm.salary.dto.WithdrawApproveRequest;
import com.seedcrm.crm.salary.dto.WithdrawCreateRequest;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.enums.SalarySettlementStatus;
import com.seedcrm.crm.salary.enums.WithdrawStatus;
import com.seedcrm.crm.salary.service.SalaryService;
import com.seedcrm.crm.salary.service.SalarySettlementPolicyService;
import com.seedcrm.crm.salary.service.SettlementService;
import com.seedcrm.crm.salary.service.WithdrawService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/salary")
public class SalaryController {

    private final SalaryService salaryService;
    private final SettlementService settlementService;
    private final SalarySettlementPolicyService salarySettlementPolicyService;
    private final WithdrawService withdrawService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final SalaryModuleGuard salaryModuleGuard;

    public SalaryController(SalaryService salaryService,
                            SettlementService settlementService,
                            SalarySettlementPolicyService salarySettlementPolicyService,
                            WithdrawService withdrawService,
                            PermissionRequestContextResolver permissionRequestContextResolver,
                            SalaryModuleGuard salaryModuleGuard) {
        this.salaryService = salaryService;
        this.settlementService = settlementService;
        this.salarySettlementPolicyService = salarySettlementPolicyService;
        this.withdrawService = withdrawService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.salaryModuleGuard = salaryModuleGuard;
    }

    @GetMapping("/stat")
    public ApiResponse<SalaryStatResponse> stat(@RequestParam Long userId, HttpServletRequest request) {
        return ApiResponse.success(salaryService.stat(resolveReadableSalaryUserId(userId, request)));
    }

    @GetMapping("/balance")
    public ApiResponse<SalaryBalanceResponse> balance(@RequestParam Long userId, HttpServletRequest request) {
        return ApiResponse.success(salaryService.balance(resolveReadableSalaryUserId(userId, request)));
    }

    @GetMapping("/withdrawable")
    public ApiResponse<BigDecimal> withdrawable(@RequestParam Long userId, HttpServletRequest request) {
        return ApiResponse.success(withdrawService.getWithdrawableAmount(resolveReadableSalaryUserId(userId, request)));
    }

    @GetMapping("/details")
    public ApiResponse<List<SalaryDetail>> details(@RequestParam Long userId, HttpServletRequest request) {
        return ApiResponse.success(salaryService.listDetails(resolveReadableSalaryUserId(userId, request)));
    }

    @GetMapping("/settlements")
    public ApiResponse<List<SalarySettlement>> settlements(@RequestParam(required = false) Long userId,
                                                           HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        return ApiResponse.success(settlementService.listSettlements(resolveSettlementUserId(userId, context)));
    }

    @GetMapping("/withdraws")
    public ApiResponse<List<WithdrawRecord>> withdraws(@RequestParam(required = false) Long userId,
                                                       HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        Long resolvedUserId = resolveSettlementUserId(userId, context);
        if (resolvedUserId == null) {
            salaryModuleGuard.checkUpdate(context);
        } else {
            salaryModuleGuard.checkView(context, resolvedUserId);
        }
        return ApiResponse.success(withdrawService.listWithdraws(resolvedUserId));
    }

    @GetMapping("/settlement-policies")
    public ApiResponse<List<SalarySettlementPolicyDtos.PolicyResponse>> settlementPolicies(HttpServletRequest request) {
        requireSettlementOperator(request);
        return ApiResponse.success(salarySettlementPolicyService.listPolicies());
    }

    @PostMapping("/settlement-policies/save-draft")
    public ApiResponse<SalarySettlementPolicyDtos.PolicyResponse> saveSettlementPolicyDraft(
            @RequestBody SalarySettlementPolicyDtos.SavePolicyRequest request,
            HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = requireSettlementOperator(httpServletRequest);
        return ApiResponse.success(salarySettlementPolicyService.saveDraft(request, context));
    }

    @PostMapping("/settlement-policies/publish")
    public ApiResponse<SalarySettlementPolicyDtos.PolicyResponse> publishSettlementPolicy(
            @RequestBody SalarySettlementPolicyDtos.PolicyStatusRequest request,
            HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = requireSettlementOperator(httpServletRequest);
        return ApiResponse.success(salarySettlementPolicyService.publish(request, context));
    }

    @PostMapping("/settlement-policies/disable")
    public ApiResponse<SalarySettlementPolicyDtos.PolicyResponse> disableSettlementPolicy(
            @RequestBody SalarySettlementPolicyDtos.PolicyStatusRequest request,
            HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = requireSettlementOperator(httpServletRequest);
        return ApiResponse.success(salarySettlementPolicyService.disable(request, context));
    }

    @PostMapping("/settlement-policies/simulate")
    public ApiResponse<SalarySettlementPolicyDtos.SimulateResponse> simulateSettlementPolicy(
            @RequestBody SalarySettlementPolicyDtos.SimulateRequest request,
            HttpServletRequest httpServletRequest) {
        requireSettlementOperator(httpServletRequest);
        return ApiResponse.success(salarySettlementPolicyService.simulate(request));
    }

    @PostMapping("/recalculate")
    public ApiResponse<List<SalaryDetail>> recalculate(@RequestBody SalaryRecalculateRequest request,
                                                       HttpServletRequest httpServletRequest) {
        requireSettlementOperator(httpServletRequest);
        return ApiResponse.success(salaryService.recalculateForPlanOrder(request == null ? null : request.getPlanOrderId()));
    }

    @PostMapping("/settlement/create")
    public ApiResponse<SalarySettlement> createSettlement(@RequestBody SalarySettlementCreateRequest request,
                                                          HttpServletRequest httpServletRequest) {
        requireSettlementOperator(httpServletRequest);
        return ApiResponse.success(settlementService.createSettlement(request));
    }

    @PostMapping("/settlement/confirm")
    public ApiResponse<SalarySettlement> confirmSettlement(@RequestBody SalarySettlementStatusRequest request,
                                                           HttpServletRequest httpServletRequest) {
        requireSettlementOperator(httpServletRequest);
        return ApiResponse.success(settlementService.updateStatus(
                request == null ? null : request.getSettlementId(), SalarySettlementStatus.CONFIRMED));
    }

    @PostMapping("/settlement/pay")
    public ApiResponse<SalarySettlement> paySettlement(@RequestBody SalarySettlementStatusRequest request,
                                                       HttpServletRequest httpServletRequest) {
        requireSettlementOperator(httpServletRequest);
        return ApiResponse.success(settlementService.updateStatus(
                request == null ? null : request.getSettlementId(), SalarySettlementStatus.PAID));
    }

    @PostMapping("/withdraw")
    public ApiResponse<WithdrawRecord> withdraw(@RequestBody WithdrawCreateRequest request,
                                                HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        return ApiResponse.success(withdrawService.createWithdraw(resolveWithdrawCreateRequest(request, context)));
    }

    @PostMapping("/withdraw/approve")
    public ApiResponse<WithdrawRecord> approveWithdraw(@RequestBody WithdrawApproveRequest request,
                                                       HttpServletRequest httpServletRequest) {
        requireSettlementOperator(httpServletRequest);
        return ApiResponse.success(withdrawService.approveWithdraw(
                request == null ? null : request.getWithdrawId(),
                WithdrawStatus.fromCode(request == null ? null : request.getStatus()),
                request == null ? null : request.getAuditRemark()));
    }

    private Long resolveReadableSalaryUserId(Long requestedUserId, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        salaryModuleGuard.checkView(context, requestedUserId);
        return requestedUserId;
    }

    private Long resolveSettlementUserId(Long requestedUserId, PermissionRequestContext context) {
        if (isSettlementOperator(context)) {
            return requestedUserId;
        }
        return context.getCurrentUserId();
    }

    private PermissionRequestContext requireSettlementOperator(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        if (!isSettlementOperator(context)) {
            throw new BusinessException("薪酬结算操作仅限管理员或财务");
        }
        salaryModuleGuard.checkUpdate(context);
        return context;
    }

    private WithdrawCreateRequest resolveWithdrawCreateRequest(WithdrawCreateRequest request,
                                                               PermissionRequestContext context) {
        if (request == null) {
            throw new BusinessException("request body is required");
        }
        WithdrawCreateRequest resolved = new WithdrawCreateRequest();
        resolved.setAmount(request.getAmount());
        resolved.setSubjectType(request.getSubjectType());
        resolved.setRoleCode(request.getRoleCode());
        if (isSettlementOperator(context)) {
            salaryModuleGuard.checkUpdate(context);
            resolved.setUserId(request.getUserId());
            return resolved;
        }

        Long currentUserId = context == null ? null : context.getCurrentUserId();
        salaryModuleGuard.checkView(context, currentUserId);
        if (request.getUserId() != null && !request.getUserId().equals(currentUserId)) {
            throw new BusinessException("只能为当前登录人发起提现");
        }
        resolved.setUserId(currentUserId);
        resolved.setRoleCode(context.getRoleCode());
        // 普通登录人只能按自身员工身份发起；外部分销提现由分销接口/财务台按配置处理。
        resolved.setSubjectType("INTERNAL_STAFF");
        return resolved;
    }

    private boolean isSettlementOperator(PermissionRequestContext context) {
        String roleCode = context == null || context.getRoleCode() == null ? "" : context.getRoleCode();
        return "ADMIN".equalsIgnoreCase(roleCode) || "FINANCE".equalsIgnoreCase(roleCode);
    }
}
