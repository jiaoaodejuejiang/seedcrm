package com.seedcrm.crm.order.dto;

import lombok.Data;

@Data
public class OrderServiceDetailDTO {

    private Long orderId;
    private String serviceRequirement;
    private String serviceDetailJson;
}
