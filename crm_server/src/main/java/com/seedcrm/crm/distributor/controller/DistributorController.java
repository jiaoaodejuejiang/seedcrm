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

    public DistributorController(DistributorService distributorService,
                                 DistributorSettlementService distributorSettlementService) {
        this.distributorService = distributorService;
        this.distributorSettlementService = distributorSettlementService;
    }

    @PostMapping("/add")
    public ApiResponse<Distributor> add(@RequestBody DistributorCreateRequest request) {
        return ApiResponse.success(distributorService.createDistributor(request));
    }

    @PostMapping("/rule/add")
    public ApiResponse<DistributorRule> addRule(@RequestBody DistributorRuleCreateRequest request) {
        return ApiResponse.success(distributorService.saveRule(request));
    }

    @GetMapping("/stats")
    public ApiResponse<DistributorStatsResponse> stats(@RequestParam("id") Long distributorId) {
        return ApiResponse.success(distributorService.getStats(distributorId));
    }

    @GetMapping("/withdrawable")
    public ApiResponse<BigDecimal> withdrawable(@RequestParam("id") Long distributorId) {
        return ApiResponse.success(distributorSettlementService.getWithdrawableAmount(distributorId));
    }

    @PostMapping("/settlement/create")
    public ApiResponse<DistributorSettlement> createSettlement(@RequestBody DistributorSettlementCreateRequest request) {
        return ApiResponse.success(distributorSettlementService.createSettlement(request));
    }

    @PostMapping("/settlement/confirm")
    public ApiResponse<DistributorSettlement> confirmSettlement(@RequestBody DistributorSettlementStatusRequest request) {
        return ApiResponse.success(distributorSettlementService.updateSettlementStatus(
                request == null ? null : request.getSettlementId(),
                DistributorSettlementStatus.CONFIRMED));
    }

    @PostMapping("/settlement/pay")
    public ApiResponse<DistributorSettlement> paySettlement(@RequestBody DistributorSettlementStatusRequest request) {
        return ApiResponse.success(distributorSettlementService.updateSettlementStatus(
                request == null ? null : request.getSettlementId(),
                DistributorSettlementStatus.PAID));
    }

    @PostMapping("/withdraw")
    public ApiResponse<DistributorWithdraw> withdraw(@RequestBody DistributorWithdrawCreateRequest request) {
        return ApiResponse.success(distributorSettlementService.createWithdraw(request));
    }

    @PostMapping("/withdraw/approve")
    public ApiResponse<DistributorWithdraw> approveWithdraw(@RequestBody DistributorWithdrawApproveRequest request) {
        return ApiResponse.success(distributorSettlementService.approveWithdraw(
                request == null ? null : request.getWithdrawId(),
                DistributorWithdrawStatus.fromCode(request == null ? null : request.getStatus())));
    }
}
