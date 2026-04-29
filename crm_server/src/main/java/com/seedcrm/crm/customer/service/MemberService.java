package com.seedcrm.crm.customer.service;

import com.seedcrm.crm.customer.dto.MemberResponses.MemberListResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;

public interface MemberService {

    MemberListResponse listMembers(String sourceTab,
                                   String phone,
                                   String name,
                                   String externalMemberId,
                                   Integer page,
                                   Integer pageSize,
                                   PermissionRequestContext context);
}
