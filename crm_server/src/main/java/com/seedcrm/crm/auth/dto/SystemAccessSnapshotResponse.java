package com.seedcrm.crm.auth.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SystemAccessSnapshotResponse {

    private List<SystemAccessMenuResponse> menus = new ArrayList<>();
    private List<SystemAccessRoleResponse> roles = new ArrayList<>();
    private List<SystemAccessPermissionResponse> permissions = new ArrayList<>();
}
