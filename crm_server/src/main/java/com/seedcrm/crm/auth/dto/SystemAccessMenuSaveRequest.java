package com.seedcrm.crm.auth.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SystemAccessMenuSaveRequest {

    private Long id;
    private String menuGroup;
    private String menuName;
    private String routePath;
    private String moduleCode;
    private String permissionCode;
    private String componentKey;
    private Integer isEnabled;
    private Integer sortOrder;
    private List<String> roleCodes = new ArrayList<>();
}
