package com.seedcrm.crm.finance.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceLedgerBoundaryResponse {

    private Boolean onlyModeEnabled;
    private Boolean refundSalaryReversalRequired;
    private Boolean distributorWithdrawRegisterOnly;
    private Integer runtimeConsumed;
    private String title;
    private String overviewDescription;
    private String settlementNotice;
    private String withdrawNotice;
    private String refundNotice;
    private String effectiveScope;
    private List<String> blockedFundActions;
}
