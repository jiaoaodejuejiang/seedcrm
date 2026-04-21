package com.seedcrm.crm.order.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderPayDTO {

    private Long orderId;
    private BigDecimal deposit;
    private String remark;
}
