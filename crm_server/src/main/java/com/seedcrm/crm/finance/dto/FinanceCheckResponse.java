package com.seedcrm.crm.finance.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCheckResponse {

    private Integer totalCount;
    private Integer matchCount;
    private Integer mismatchCount;
    private List<FinanceCheckItemResponse> records;
}
