package com.seedcrm.crm.salary.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class WithdrawCreateRequest {

    private Long userId;
    private BigDecimal amount;
}
