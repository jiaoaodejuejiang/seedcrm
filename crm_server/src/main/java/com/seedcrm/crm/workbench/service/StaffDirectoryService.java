package com.seedcrm.crm.workbench.service;

import com.seedcrm.crm.role.entity.RoleConfig;
import com.seedcrm.crm.role.service.RoleConfigService;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StaffMemberOption;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StaffRoleOptionResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StaffDirectoryService {

    private static final List<StaffMemberOption> DEFAULT_STAFF = List.of(
            new StaffMemberOption(1001L, "顾问A", "CONSULTANT"),
            new StaffMemberOption(1002L, "顾问B", "CONSULTANT"),
            new StaffMemberOption(2001L, "医生A", "DOCTOR"),
            new StaffMemberOption(2002L, "医生B", "DOCTOR"),
            new StaffMemberOption(3001L, "助理A", "ASSISTANT"),
            new StaffMemberOption(3002L, "助理B", "ASSISTANT"));

    private static final Map<String, String> DEFAULT_ROLE_NAMES = Map.of(
            "CONSULTANT", "顾问",
            "DOCTOR", "医生",
            "ASSISTANT", "助理");

    private final RoleConfigService roleConfigService;

    public StaffDirectoryService(RoleConfigService roleConfigService) {
        this.roleConfigService = roleConfigService;
    }

    public List<StaffRoleOptionResponse> listRoleOptions() {
        Map<String, String> roleNameMap = buildRoleNameMap();
        Map<String, List<StaffMemberOption>> staffGroup = new LinkedHashMap<>();
        roleNameMap.keySet().forEach(roleCode -> staffGroup.put(roleCode, new ArrayList<>()));
        for (StaffMemberOption staff : DEFAULT_STAFF) {
            staffGroup.computeIfAbsent(normalizeRoleCode(staff.getRoleCode()), key -> new ArrayList<>()).add(staff);
        }

        List<StaffRoleOptionResponse> responses = new ArrayList<>();
        for (Map.Entry<String, String> entry : roleNameMap.entrySet()) {
            List<StaffMemberOption> staffMembers = staffGroup.getOrDefault(entry.getKey(), List.of())
                    .stream()
                    .sorted(Comparator.comparing(StaffMemberOption::getUserId))
                    .toList();
            responses.add(new StaffRoleOptionResponse(entry.getKey(), entry.getValue(), staffMembers));
        }
        return responses;
    }

    public String getRoleName(String roleCode) {
        return buildRoleNameMap().getOrDefault(normalizeRoleCode(roleCode), normalizeRoleCode(roleCode));
    }

    public String getUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return DEFAULT_STAFF.stream()
                .filter(item -> Objects.equals(item.getUserId(), userId))
                .map(StaffMemberOption::getUserName)
                .findFirst()
                .orElse("Employee#" + userId);
    }

    private Map<String, String> buildRoleNameMap() {
        Map<String, String> roleNameMap = new LinkedHashMap<>(DEFAULT_ROLE_NAMES);
        List<RoleConfig> configs = roleConfigService.list().stream()
                .filter(role -> role.getIsEnabled() == null || role.getIsEnabled() == 1)
                .sorted(Comparator.comparing(RoleConfig::getSort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(RoleConfig::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        for (RoleConfig config : configs) {
            String roleCode = normalizeRoleCode(config.getRoleCode());
            if (!StringUtils.hasText(roleCode)) {
                continue;
            }
            roleNameMap.put(roleCode, StringUtils.hasText(config.getRoleName()) ? config.getRoleName() : roleCode);
        }
        return roleNameMap;
    }

    private String normalizeRoleCode(String roleCode) {
        return roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
    }
}
