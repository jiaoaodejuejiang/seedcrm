package com.seedcrm.crm.distributor.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.distributor.dto.DistributorCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorStatsResponse;
import com.seedcrm.crm.distributor.entity.Distributor;
import com.seedcrm.crm.distributor.service.DistributorService;
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

    public DistributorController(DistributorService distributorService) {
        this.distributorService = distributorService;
    }

    @PostMapping("/add")
    public ApiResponse<Distributor> add(@RequestBody DistributorCreateRequest request) {
        return ApiResponse.success(distributorService.createDistributor(request));
    }

    @GetMapping("/stats")
    public ApiResponse<DistributorStatsResponse> stats(@RequestParam("id") Long distributorId) {
        return ApiResponse.success(distributorService.getStats(distributorId));
    }
}
