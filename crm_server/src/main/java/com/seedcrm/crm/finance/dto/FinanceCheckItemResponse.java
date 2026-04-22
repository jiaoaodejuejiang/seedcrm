package com.seedcrm.crm.finance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCheckItemResponse {

    private String bizType;
    private Long bizId;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private String status;
}
