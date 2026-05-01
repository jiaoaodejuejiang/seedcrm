package com.seedcrm.crm.systemconfig.service;

import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.systemconfig.dto.SystemGoLiveDtos;

public interface SystemGoLiveService {

    SystemGoLiveDtos.SummaryResponse summary();

    SystemGoLiveDtos.OperationResponse initialize(SystemGoLiveDtos.InitializeRequest request,
                                                  PermissionRequestContext context);

    SystemGoLiveDtos.OperationResponse clearTestData(SystemGoLiveDtos.ClearTestDataRequest request,
                                                     PermissionRequestContext context);
}
