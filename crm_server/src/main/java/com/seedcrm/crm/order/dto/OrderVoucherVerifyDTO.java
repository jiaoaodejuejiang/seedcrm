package com.seedcrm.crm.order.dto;

import lombok.Data;

@Data
public class OrderVoucherVerifyDTO {

    private Long orderId;
    private String verificationCode;
    private String verificationMethod;
}
