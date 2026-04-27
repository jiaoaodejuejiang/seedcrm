package com.seedcrm.crm.auth.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUser {

    private String username;
    private String displayName;
    private String roleCode;
    private String roleName;
    private String dataScope;
    private Long userId;
    private Long storeId;
    private String storeName;
    private List<Long> teamMemberIds;
    private Long boundCustomerUserId;
    private List<String> allowedModules;
}
