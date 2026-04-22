package com.seedcrm.crm.distributor.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistributorStatsResponse {

    private Long distributorId;
    private Long clueCount;
    private Long dealCustomerCount;
    private Long orderCount;
    private BigDecimal totalIncome;
    private BigDecimal settledIncome;
    private BigDecimal unsettledIncome;
    private BigDecimal withdrawableAmount;
}
