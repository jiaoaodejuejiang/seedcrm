package com.seedcrm.crm.order.dto;

import lombok.Data;

@Data
public class OrderServiceDetailDTO {

    private Long orderId;
    private String serviceRequirement;
    private String serviceDetailJson;
    private Long serviceTemplateId;
    private Long serviceTemplateBindingId;
    private String serviceTemplateCode;
    private String serviceTemplateName;
    private String serviceTemplateTitle;
    private String serviceTemplateLayoutMode;
    private String serviceTemplateConfigJson;
    private String serviceTemplateSnapshotJson;
}
