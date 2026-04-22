package com.seedcrm.crm.salary.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalaryBalanceResponse {

    private Long userId;
    private BigDecimal unsettledAmount;
    private BigDecimal settledAmount;
    private BigDecimal withdrawnAmount;
    private BigDecimal withdrawableAmount;
}
