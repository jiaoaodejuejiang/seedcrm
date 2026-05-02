package com.seedcrm.crm.order.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderActionDTO {

    private Long orderId;
    private String remark;
    private String sourceSurface;
    private String appointmentReasonType;
    private String refundScene;
    private BigDecimal refundAmount;
    private String refundReasonType;
    private String refundReason;
    private BigDecimal serviceRefundAmount;
    private String idempotencyKey;
    private String outOrderNo;
    private String outRefundNo;
    private String externalRefundId;
    private String itemOrderId;
    private String notifyUrl;
    private String platformChannel;
    private Boolean reverseSalary;
    private Boolean reverseDistributor;
    private Boolean reverseCustomerService;
    private Boolean reverseStorePerformance;
}
