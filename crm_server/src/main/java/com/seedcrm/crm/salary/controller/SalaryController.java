package com.seedcrm.crm.salary.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.salary.dto.SalaryStatResponse;
import com.seedcrm.crm.salary.service.SalaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/salary")
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @GetMapping("/stat")
    public ApiResponse<SalaryStatResponse> stat(@RequestParam Long userId) {
        return ApiResponse.success(salaryService.stat(userId));
    }
}
