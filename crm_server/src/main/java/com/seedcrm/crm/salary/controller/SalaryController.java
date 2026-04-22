package com.seedcrm.crm.salary.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.salary.dto.SalaryBalanceResponse;
import com.seedcrm.crm.salary.dto.SalaryRecalculateRequest;
import com.seedcrm.crm.salary.dto.SalarySettlementCreateRequest;
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
import com.seedcrm.crm.salary.service.SettlementService;
import com.seedcrm.crm.salary.service.WithdrawService;
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
    private final WithdrawService withdrawService;

    public SalaryController(SalaryService salaryService,
                            SettlementService settlementService,
                            WithdrawService withdrawService) {
        this.salaryService = salaryService;
        this.settlementService = settlementService;
        this.withdrawService = withdrawService;
    }

    @GetMapping("/stat")
    public ApiResponse<SalaryStatResponse> stat(@RequestParam Long userId) {
        return ApiResponse.success(salaryService.stat(userId));
    }

    @GetMapping("/balance")
    public ApiResponse<SalaryBalanceResponse> balance(@RequestParam Long userId) {
        return ApiResponse.success(salaryService.balance(userId));
    }

    @GetMapping("/withdrawable")
    public ApiResponse<BigDecimal> withdrawable(@RequestParam Long userId) {
        return ApiResponse.success(withdrawService.getWithdrawableAmount(userId));
    }

    @PostMapping("/recalculate")
    public ApiResponse<List<SalaryDetail>> recalculate(@RequestBody SalaryRecalculateRequest request) {
        return ApiResponse.success(salaryService.recalculateForPlanOrder(request == null ? null : request.getPlanOrderId()));
    }

    @PostMapping("/settlement/create")
    public ApiResponse<SalarySettlement> createSettlement(@RequestBody SalarySettlementCreateRequest request) {
        return ApiResponse.success(settlementService.createSettlement(request));
    }

    @PostMapping("/settlement/confirm")
    public ApiResponse<SalarySettlement> confirmSettlement(@RequestBody SalarySettlementStatusRequest request) {
        return ApiResponse.success(settlementService.updateStatus(
                request == null ? null : request.getSettlementId(), SalarySettlementStatus.CONFIRMED));
    }

    @PostMapping("/settlement/pay")
    public ApiResponse<SalarySettlement> paySettlement(@RequestBody SalarySettlementStatusRequest request) {
        return ApiResponse.success(settlementService.updateStatus(
                request == null ? null : request.getSettlementId(), SalarySettlementStatus.PAID));
    }

    @PostMapping("/withdraw")
    public ApiResponse<WithdrawRecord> withdraw(@RequestBody WithdrawCreateRequest request) {
        return ApiResponse.success(withdrawService.createWithdraw(request));
    }

    @PostMapping("/withdraw/approve")
    public ApiResponse<WithdrawRecord> approveWithdraw(@RequestBody WithdrawApproveRequest request) {
        return ApiResponse.success(withdrawService.approveWithdraw(
                request == null ? null : request.getWithdrawId(),
                WithdrawStatus.fromCode(request == null ? null : request.getStatus())));
    }
}
