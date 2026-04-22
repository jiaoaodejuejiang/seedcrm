package com.seedcrm.crm.finance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceBalanceResponse {

    private Long accountId;
    private String ownerType;
    private Long ownerId;
    private BigDecimal balance;
}
