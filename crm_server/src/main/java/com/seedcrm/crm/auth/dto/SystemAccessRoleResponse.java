package com.seedcrm.crm.auth.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SystemAccessRoleResponse {

    private Long id;
    private String roleCode;
    private String roleName;
    private String dataScope;
    private String roleType;
    private Integer isEnabled;
    private Integer sortOrder;
    private List<String> moduleCodes = new ArrayList<>();
    private List<String> menuRoutes = new ArrayList<>();
    private List<String> permissionCodes = new ArrayList<>();
}
