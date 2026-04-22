package com.seedcrm.crm.distributor.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class DistributorWithdrawCreateRequest {

    private Long distributorId;
    private BigDecimal amount;
}
