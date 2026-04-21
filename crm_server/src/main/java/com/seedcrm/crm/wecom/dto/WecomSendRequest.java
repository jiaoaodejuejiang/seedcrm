package com.seedcrm.crm.wecom.dto;

import lombok.Data;

@Data
public class WecomSendRequest {

    private Long customerId;
    private String message;
}
