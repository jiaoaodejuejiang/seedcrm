package com.seedcrm.crm.distributor.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.distributor.dto.DistributorCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorRuleCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorSettlementCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorSettlementStatusRequest;
import com.seedcrm.crm.distributor.dto.DistributorStatsResponse;
import com.seedcrm.crm.distributor.dto.DistributorWithdrawApproveRequest;
import com.seedcrm.crm.distributor.dto.DistributorWithdrawCreateRequest;
import com.seedcrm.crm.distributor.entity.Distributor;
import com.seedcrm.crm.distributor.entity.DistributorRule;
import com.seedcrm.crm.distributor.entity.DistributorSettlement;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.distributor.enums.DistributorSettlementStatus;
import com.seedcrm.crm.distributor.enums.DistributorWithdrawStatus;
import com.seedcrm.crm.distributor.service.DistributorSettlementService;
import com.seedcrm.crm.distributor.service.DistributorService;
import com.seedcrm.crm.permission.support.FinancePermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/distributor")
public class DistributorController {

    private final DistributorService distributorService;
    private final DistributorSettlementService distributorSettlementService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final FinancePermissionGuard financePermissionGuard;

    public DistributorController(DistributorService distributorService,
                                 DistributorSettlementService distributorSettlementService,
                                 PermissionRequestContextResolver permissionRequestContextResolver,
                                 FinancePermissionGuard financePermissionGuard) {
        this.distributorService = distributorService;
        this.distributorSettlementService = distributorSettlementService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.financePermissionGuard = financePermissionGuard;
    }

    @PostMapping("/add")
    public ApiResponse<Distributor> add(@RequestBody DistributorCreateRequest request,
                                        HttpServletRequest servletRequest) {
        checkFinanceUpdate(servletRequest);
        return ApiResponse.success(distributorService.createDistributor(request));
    }

    @PostMapping("/rule/add")
    public ApiResponse<DistributorRule> addRule(@RequestBody DistributorRuleCreateRequest request,
                                                HttpServletRequest servletRequest) {
        checkFinanceUpdate(servletRequest);
        return ApiResponse.success(distributorService.saveRule(request));
    }

    @GetMapping("/stats")
    public ApiResponse<DistributorStatsResponse> stats(@RequestParam("id") Long distributorId,
                                                       HttpServletRequest request) {
        checkFinanceView(request);
        return ApiResponse.success(distributorService.getStats(distributorId));
    }

    @GetMapping("/withdrawable")
    public ApiResponse<BigDecimal> withdrawable(@RequestParam("id") Long distributorId,
                                                HttpServletRequest request) {
        checkFinanceView(request);
        return ApiResponse.success(distributorSettlementService.getWithdrawableAmount(distributorId));
    }

    @PostMapping("/settlement/create")
    public ApiResponse<DistributorSettlement> createSettlement(@RequestBody DistributorSettlementCreateRequest request,
                                                               HttpServletRequest servletRequest) {
        checkFinanceUpdate(servletRequest);
        return ApiResponse.success(distributorSettlementService.createSettlement(request));
    }

    @PostMapping("/settlement/confirm")
    public ApiResponse<DistributorSettlement> confirmSettlement(@RequestBody DistributorSettlementStatusRequest request,
                                                                HttpServletRequest servletRequest) {
        checkFinanceUpdate(servletRequest);
        return ApiResponse.success(distributorSettlementService.updateSettlementStatus(
                request == null ? null : request.getSettlementId(),
                DistributorSettlementStatus.CONFIRMED));
    }

    @PostMapping("/settlement/pay")
    public ApiResponse<DistributorSettlement> paySettlement(@RequestBody DistributorSettlementStatusRequest request,
                                                           HttpServletRequest servletRequest) {
        checkFinanceUpdate(servletRequest);
        return ApiResponse.success(distributorSettlementService.updateSettlementStatus(
                request == null ? null : request.getSettlementId(),
                DistributorSettlementStatus.PAID));
    }

    @PostMapping("/withdraw")
    public ApiResponse<DistributorWithdraw> withdraw(@RequestBody DistributorWithdrawCreateRequest request,
                                                     HttpServletRequest servletRequest) {
        checkFinanceUpdate(servletRequest);
        return ApiResponse.success(distributorSettlementService.createWithdraw(request));
    }

    @PostMapping("/withdraw/approve")
    public ApiResponse<DistributorWithdraw> approveWithdraw(@RequestBody DistributorWithdrawApproveRequest request,
                                                           HttpServletRequest servletRequest) {
        checkFinanceUpdate(servletRequest);
        return ApiResponse.success(distributorSettlementService.approveWithdraw(
                request == null ? null : request.getWithdrawId(),
                DistributorWithdrawStatus.fromCode(request == null ? null : request.getStatus())));
    }

    private void checkFinanceView(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        financePermissionGuard.checkView(context);
    }

    private void checkFinanceUpdate(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        financePermissionGuard.checkUpdate(context);
    }
}
