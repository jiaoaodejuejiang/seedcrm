package com.seedcrm.crm.planorder.dto;

import lombok.Data;

@Data
public class PlanOrderServiceFormStateResponse {

    private Long planOrderId;
    private Long orderId;
    private Boolean saved = false;
    private Boolean printed = false;
    private Boolean confirmed = false;
    private Boolean stale = false;
    private String serviceDetailHash;
    private String projectionVersion;
    private String printStatus;
    private Integer printCount = 0;
    private String printedAt;
    private Long printedByUserId;
    private String printedByRoleCode;
    private String confirmationStatus;
    private String confirmedAt;
    private Long confirmedByUserId;
    private String confirmedByRoleCode;
    private String staleReason;
}
