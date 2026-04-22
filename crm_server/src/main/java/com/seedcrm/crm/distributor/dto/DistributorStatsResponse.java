package com.seedcrm.crm.distributor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DistributorStatsResponse {

    private Long distributorId;
    private Long clueCount;
    private Long dealCustomerCount;
    private Long orderCount;
}
