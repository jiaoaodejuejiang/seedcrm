package com.seedcrm.crm.permission.dto;

import java.util.List;
import lombok.Data;

@Data
public class PermissionCheckRequest {

    private String moduleCode;

    private String actionCode;

    private String roleCode;

    private String dataScope;

    private Long currentUserId;

    private Long resourceOwnerId;

    private Long currentStoreId;

    private Long resourceStoreId;

    private List<Long> teamMemberIds;

    private Long boundCustomerUserId;

    private String currentPartnerCode;

    private String resourcePartnerCode;
}
