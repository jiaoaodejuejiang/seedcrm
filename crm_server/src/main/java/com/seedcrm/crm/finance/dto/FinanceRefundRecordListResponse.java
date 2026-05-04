package com.seedcrm.crm.finance.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceRefundRecordListResponse {

    private List<FinanceRefundRecordResponse> records;
    private Long total;
    private Integer page;
    private Integer pageSize;
}
