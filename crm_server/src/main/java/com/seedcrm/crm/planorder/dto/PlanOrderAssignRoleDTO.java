package com.seedcrm.crm.planorder.dto;

import lombok.Data;

@Data
public class PlanOrderAssignRoleDTO {

    private Long planOrderId;
    private String roleCode;
    private Long userId;
}
