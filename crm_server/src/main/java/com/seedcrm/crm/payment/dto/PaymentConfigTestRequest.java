package com.seedcrm.crm.payment.dto;

import lombok.Data;

@Data
public class PaymentConfigTestRequest {

    private String channel;
    private Integer enabled;
    private String merchantName;
    private String mchId;
    private String appId;
    private String apiV3Key;
    private String serialNo;
    private String privateKeyPem;
    private String notifyPath;
    private String refundNotifyPath;
    private String apiBaseUrl;
}
