package com.seedcrm.crm.order.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderCreateDTO {

    private Long clueId;
    private Long customerId;
    private Integer type;
    private BigDecimal amount;
    private BigDecimal deposit;
    private String remark;
}
