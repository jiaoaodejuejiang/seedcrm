package com.seedcrm.crm.finance.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.finance.dto.FinanceBalanceResponse;
import com.seedcrm.crm.finance.dto.FinanceCheckResponse;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.service.FinanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/finance")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/balance")
    public ApiResponse<FinanceBalanceResponse> balance(@RequestParam String ownerType,
                                                       @RequestParam(required = false) Long ownerId) {
        return ApiResponse.success(financeService.getBalance(AccountOwnerType.fromCode(ownerType), ownerId));
    }

    @PostMapping("/check")
    public ApiResponse<FinanceCheckResponse> check() {
        return ApiResponse.success(financeService.check());
    }
}
