package com.seedcrm.crm.permission.support;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class PermissionRequestContext {

    private String roleCode;

    private String dataScope;

    private Long currentUserId;

    private Long currentStoreId;

    private String currentStoreName;

    private Long resourceStoreId;

    private Long boundCustomerUserId;

    private String currentPartnerCode;

    private String resourcePartnerCode;

    private List<Long> teamMemberIds = new ArrayList<>();
}
