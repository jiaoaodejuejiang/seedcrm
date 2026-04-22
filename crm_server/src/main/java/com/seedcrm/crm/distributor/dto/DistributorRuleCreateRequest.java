package com.seedcrm.crm.distributor.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class DistributorRuleCreateRequest {

    private Long distributorId;
    private String ruleType;
    private BigDecimal ruleValue;
    private Integer isActive;
}
