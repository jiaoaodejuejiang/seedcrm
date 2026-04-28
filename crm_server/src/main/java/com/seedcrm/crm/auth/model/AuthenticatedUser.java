package com.seedcrm.crm.auth.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
    private List<AuthMenuNode> menuTree = new ArrayList<>();
    private List<String> menuRoutes = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private String defaultRoute;

    public AuthenticatedUser(String username,
                             String displayName,
                             String roleCode,
                             String roleName,
                             String dataScope,
                             Long userId,
                             Long storeId,
                             String storeName,
                             List<Long> teamMemberIds,
                             Long boundCustomerUserId,
                             List<String> allowedModules) {
        this.username = username;
        this.displayName = displayName;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.dataScope = dataScope;
        this.userId = userId;
        this.storeId = storeId;
        this.storeName = storeName;
        this.teamMemberIds = teamMemberIds;
        this.boundCustomerUserId = boundCustomerUserId;
        this.allowedModules = allowedModules;
    }
}
