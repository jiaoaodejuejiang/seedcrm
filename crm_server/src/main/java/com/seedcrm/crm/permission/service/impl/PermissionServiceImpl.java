package com.seedcrm.crm.permission.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.entity.PermissionPolicy;
import com.seedcrm.crm.permission.mapper.PermissionPolicyMapper;
import com.seedcrm.crm.permission.service.PermissionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final PermissionPolicyMapper permissionPolicyMapper;

    public PermissionServiceImpl(PermissionPolicyMapper permissionPolicyMapper) {
        this.permissionPolicyMapper = permissionPolicyMapper;
    }

    @Override
    public List<PermissionPolicy> listPolicies() {
        return permissionPolicyMapper.selectList(Wrappers.<PermissionPolicy>lambdaQuery()
                .orderByAsc(PermissionPolicy::getModuleCode)
                .orderByAsc(PermissionPolicy::getActionCode)
                .orderByAsc(PermissionPolicy::getRoleCode)
                .orderByAsc(PermissionPolicy::getDataScope)
                .orderByAsc(PermissionPolicy::getId));
    }

    @Override
    public PermissionPolicy savePolicy(PermissionPolicy policy) {
        if (policy == null
                || !StringUtils.hasText(policy.getModuleCode())
                || !StringUtils.hasText(policy.getActionCode())
                || !StringUtils.hasText(policy.getRoleCode())
                || !StringUtils.hasText(policy.getDataScope())) {
            throw new BusinessException("moduleCode, actionCode, roleCode and dataScope are required");
        }
        normalize(policy);
        PermissionPolicy existing = permissionPolicyMapper.selectOne(Wrappers.<PermissionPolicy>lambdaQuery()
                .eq(PermissionPolicy::getModuleCode, policy.getModuleCode())
                .eq(PermissionPolicy::getActionCode, policy.getActionCode())
                .eq(PermissionPolicy::getRoleCode, policy.getRoleCode())
                .eq(PermissionPolicy::getDataScope, policy.getDataScope())
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            policy.setIsEnabled(policy.getIsEnabled() == null ? 1 : policy.getIsEnabled());
            policy.setCreatedAt(now);
            policy.setUpdatedAt(now);
            if (permissionPolicyMapper.insert(policy) <= 0) {
                throw new BusinessException("failed to save permission policy");
            }
            return policy;
        }
        existing.setConditionRule(policy.getConditionRule());
        existing.setIsEnabled(policy.getIsEnabled() == null ? existing.getIsEnabled() : policy.getIsEnabled());
        existing.setUpdatedAt(now);
        if (permissionPolicyMapper.updateById(existing) <= 0) {
            throw new BusinessException("failed to update permission policy");
        }
        return existing;
    }

    @Override
    public PermissionCheckResponse check(PermissionCheckRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getModuleCode())
                || !StringUtils.hasText(request.getActionCode())
                || !StringUtils.hasText(request.getRoleCode())) {
            throw new BusinessException("moduleCode, actionCode and roleCode are required");
        }
        String moduleCode = normalize(request.getModuleCode());
        String actionCode = normalize(request.getActionCode());
        String roleCode = normalize(request.getRoleCode());
        if ("ADMIN".equals(roleCode)) {
            return new PermissionCheckResponse(true, "ADMIN_BYPASS", "ALL", "admin bypass");
        }
        String expectedScope = StringUtils.hasText(request.getDataScope()) ? normalize(request.getDataScope()) : null;

        List<PermissionPolicy> policies = permissionPolicyMapper.selectList(Wrappers.<PermissionPolicy>lambdaQuery()
                .eq(PermissionPolicy::getModuleCode, moduleCode)
                .eq(PermissionPolicy::getActionCode, actionCode)
                .eq(PermissionPolicy::getRoleCode, roleCode)
                .eq(PermissionPolicy::getIsEnabled, 1)
                .orderByAsc(PermissionPolicy::getId));
        if (policies.isEmpty()) {
            return new PermissionCheckResponse(false, null, expectedScope, "no matching RBAC policy");
        }

        for (PermissionPolicy policy : policies) {
            if (expectedScope != null && !expectedScope.equalsIgnoreCase(policy.getDataScope())) {
                continue;
            }
            String scope = normalize(policy.getDataScope());
            if (matchesScope(scope, request)) {
                return new PermissionCheckResponse(
                        true,
                        buildPolicyKey(policy),
                        scope,
                        StringUtils.hasText(policy.getConditionRule()) ? policy.getConditionRule() : "allowed");
            }
        }
        return new PermissionCheckResponse(false, null, expectedScope, "ABAC scope rejected");
    }

    private boolean matchesScope(String scope, PermissionCheckRequest request) {
        return switch (scope) {
            case "ALL" -> true;
            case "SELF" -> matchesSelf(request);
            case "TEAM" -> request.getTeamMemberIds() != null
                    && request.getResourceOwnerId() != null
                    && request.getTeamMemberIds().contains(request.getResourceOwnerId());
            case "STORE" -> request.getCurrentStoreId() != null
                    && Objects.equals(request.getCurrentStoreId(), request.getResourceStoreId());
            default -> false;
        };
    }

    private boolean matchesSelf(PermissionCheckRequest request) {
        if (request.getCurrentUserId() == null) {
            return false;
        }
        return Objects.equals(request.getCurrentUserId(), request.getResourceOwnerId())
                || Objects.equals(request.getCurrentUserId(), request.getBoundCustomerUserId());
    }

    private void normalize(PermissionPolicy policy) {
        policy.setModuleCode(normalize(policy.getModuleCode()));
        policy.setActionCode(normalize(policy.getActionCode()));
        policy.setRoleCode(normalize(policy.getRoleCode()));
        policy.setDataScope(normalize(policy.getDataScope()));
        if (StringUtils.hasText(policy.getConditionRule())) {
            policy.setConditionRule(policy.getConditionRule().trim());
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String buildPolicyKey(PermissionPolicy policy) {
        return policy.getModuleCode() + ":" + policy.getActionCode() + ":" + policy.getRoleCode() + ":" + policy.getDataScope();
    }
}
