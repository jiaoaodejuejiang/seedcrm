package com.seedcrm.crm.clue.management.service;

import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.AssignmentStrategyRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.AssignmentStrategyResponse;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DedupConfigRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DedupConfigResponse;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DutyCustomerServiceBatchRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DutyCustomerServiceResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import java.util.List;

public interface ClueManagementService {

    AssignmentStrategyResponse getAssignmentStrategy(Long storeId);

    AssignmentStrategyResponse saveAssignmentStrategy(Long storeId, Long updatedBy, AssignmentStrategyRequest request);

    DedupConfigResponse getDedupConfig();

    DedupConfigResponse saveDedupConfig(DedupConfigRequest request, PermissionRequestContext context);

    List<DutyCustomerServiceResponse> listDutyCustomerServices(Long storeId);

    List<DutyCustomerServiceResponse> saveDutyCustomerServices(Long storeId, DutyCustomerServiceBatchRequest request);

    Clue autoAssignIfEnabled(Clue clue);
}
