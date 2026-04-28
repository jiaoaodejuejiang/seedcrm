package com.seedcrm.crm.auth.dto;

import lombok.Data;

@Data
public class SystemAccessPermissionResponse {

    private Long id;
    private String permissionCode;
    private String moduleCode;
    private String actionCode;
    private String permissionName;
    private Integer isEnabled;
    private Integer sortOrder;
}
