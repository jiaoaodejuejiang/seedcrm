package com.seedcrm.crm.permission.service;

import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.entity.PermissionPolicy;
import java.util.List;

public interface PermissionService {

    List<PermissionPolicy> listPolicies();

    PermissionPolicy savePolicy(PermissionPolicy policy);

    PermissionCheckResponse check(PermissionCheckRequest request);
}
