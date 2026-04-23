package com.seedcrm.crm.permission.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.entity.PermissionPolicy;
import com.seedcrm.crm.permission.mapper.PermissionPolicyMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock
    private PermissionPolicyMapper permissionPolicyMapper;

    private PermissionServiceImpl permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionServiceImpl(permissionPolicyMapper);
    }

    @Test
    void shouldAllowSelfScopedPermission() {
        PermissionPolicy policy = new PermissionPolicy();
        policy.setModuleCode("CLUE");
        policy.setActionCode("VIEW");
        policy.setRoleCode("ONLINE_CUSTOMER_SERVICE");
        policy.setDataScope("SELF");
        policy.setIsEnabled(1);
        when(permissionPolicyMapper.selectList(any())).thenReturn(List.of(policy));

        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("clue");
        request.setActionCode("view");
        request.setRoleCode("online_customer_service");
        request.setCurrentUserId(1001L);
        request.setResourceOwnerId(1001L);

        PermissionCheckResponse response = permissionService.check(request);

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getDataScope()).isEqualTo("SELF");
    }

    @Test
    void shouldRejectWhenAbacScopeDoesNotMatch() {
        PermissionPolicy policy = new PermissionPolicy();
        policy.setModuleCode("ORDER");
        policy.setActionCode("VIEW");
        policy.setRoleCode("STORE_SERVICE");
        policy.setDataScope("STORE");
        policy.setIsEnabled(1);
        when(permissionPolicyMapper.selectList(any())).thenReturn(List.of(policy));

        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setModuleCode("order");
        request.setActionCode("view");
        request.setRoleCode("store_service");
        request.setCurrentStoreId(10L);
        request.setResourceStoreId(11L);

        PermissionCheckResponse response = permissionService.check(request);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReason()).contains("ABAC");
    }
}
