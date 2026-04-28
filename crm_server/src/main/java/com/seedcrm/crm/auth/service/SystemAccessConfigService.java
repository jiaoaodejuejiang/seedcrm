package com.seedcrm.crm.auth.service;

import com.seedcrm.crm.auth.dto.SystemAccessMenuResponse;
import com.seedcrm.crm.auth.dto.SystemAccessMenuSaveRequest;
import com.seedcrm.crm.auth.dto.SystemAccessRoleResponse;
import com.seedcrm.crm.auth.dto.SystemAccessRoleSaveRequest;
import com.seedcrm.crm.auth.dto.SystemAccessSnapshotResponse;

public interface SystemAccessConfigService {

    SystemAccessSnapshotResponse snapshot();

    SystemAccessMenuResponse saveMenu(SystemAccessMenuSaveRequest request);

    SystemAccessRoleResponse saveRole(SystemAccessRoleSaveRequest request);
}
