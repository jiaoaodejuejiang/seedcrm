package com.seedcrm.crm.salary.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SalaryStatResponse {

    private Long userId;
    private Long participateOrderCount;
    private Map<String, Long> roleDistribution;
    private Long serviceCount;
}
