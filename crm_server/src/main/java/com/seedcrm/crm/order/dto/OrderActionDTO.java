package com.seedcrm.crm.order.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderActionDTO {

    private Long orderId;
    private String remark;
    private BigDecimal serviceRefundAmount;
    private Boolean reverseSalary;
    private Boolean reverseDistributor;
}
