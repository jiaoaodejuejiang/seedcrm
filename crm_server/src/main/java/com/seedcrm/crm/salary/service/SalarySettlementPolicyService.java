package com.seedcrm.crm.salary.service;

import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.salary.dto.SalarySettlementPolicyDtos;
import java.util.List;

public interface SalarySettlementPolicyService {

    List<SalarySettlementPolicyDtos.PolicyResponse> listPolicies();

    SalarySettlementPolicyDtos.PolicyResponse saveDraft(SalarySettlementPolicyDtos.SavePolicyRequest request,
                                                        PermissionRequestContext context);

    SalarySettlementPolicyDtos.PolicyResponse publish(SalarySettlementPolicyDtos.PolicyStatusRequest request,
                                                      PermissionRequestContext context);

    SalarySettlementPolicyDtos.PolicyResponse disable(SalarySettlementPolicyDtos.PolicyStatusRequest request,
                                                     PermissionRequestContext context);

    SalarySettlementPolicyDtos.SimulateResponse simulate(SalarySettlementPolicyDtos.SimulateRequest request);
}
