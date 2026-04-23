package com.seedcrm.crm.permission.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PermissionCheckResponse {

    private boolean allowed;

    private String matchedPolicy;

    private String dataScope;

    private String reason;
}
