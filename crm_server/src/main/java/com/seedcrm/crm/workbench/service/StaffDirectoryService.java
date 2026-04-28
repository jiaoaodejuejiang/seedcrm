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
            new StaffMemberOption(1L, "系统管理员", "ADMIN"),
            new StaffMemberOption(5001L, "客资主管", "CLUE_MANAGER"),
            new StaffMemberOption(1001L, "在线客服A", "ONLINE_CUSTOMER_SERVICE"),
            new StaffMemberOption(1002L, "在线客服B", "ONLINE_CUSTOMER_SERVICE"),
            new StaffMemberOption(5101L, "门店服务A", "STORE_SERVICE"),
            new StaffMemberOption(5002L, "静安店长", "STORE_MANAGER"),
            new StaffMemberOption(2001L, "摄影A", "PHOTOGRAPHER"),
            new StaffMemberOption(2002L, "摄影B", "PHOTOGRAPHER"),
            new StaffMemberOption(3001L, "化妆师A", "MAKEUP_ARTIST"),
            new StaffMemberOption(3002L, "化妆师B", "MAKEUP_ARTIST"),
            new StaffMemberOption(4001L, "选片负责人A", "PHOTO_SELECTOR"),
            new StaffMemberOption(4002L, "选片负责人B", "PHOTO_SELECTOR"),
            new StaffMemberOption(91001L, "财务专员", "FINANCE"),
            new StaffMemberOption(1101L, "私域客服A", "PRIVATE_DOMAIN_SERVICE"));

    private static final Map<String, String> DEFAULT_ROLE_NAMES = Map.of(
            "ADMIN", "管理员",
            "CLUE_MANAGER", "客资主管",
            "ONLINE_CUSTOMER_SERVICE", "在线客服",
            "STORE_SERVICE", "门店服务",
            "STORE_MANAGER", "大队长（店长）",
            "PHOTOGRAPHER", "摄影",
            "MAKEUP_ARTIST", "化妆师",
            "PHOTO_SELECTOR", "选片负责人",
            "PRIVATE_DOMAIN_SERVICE", "私域客服",
            "FINANCE", "财务");

    private static final Map<Long, String> DEFAULT_STAFF_PHONES = Map.ofEntries(
            Map.entry(1L, "13800000001"),
            Map.entry(5101L, "13800005101"),
            Map.entry(5002L, "13800005002"),
            Map.entry(2001L, "13800002001"),
            Map.entry(2002L, "13800002002"),
            Map.entry(3001L, "13800003001"),
            Map.entry(3002L, "13800003002"),
            Map.entry(4001L, "13800004001"),
            Map.entry(4002L, "13800004002"),
            Map.entry(1001L, "13800001001"),
            Map.entry(1002L, "13800001002"),
            Map.entry(5001L, "13800005001"),
            Map.entry(1101L, "13800001101"),
            Map.entry(91001L, "13800091001"));

    private static final Map<Long, String> DEFAULT_WECOM_ACCOUNTS = Map.ofEntries(
            Map.entry(1L, "admin"),
            Map.entry(5101L, "store_service"),
            Map.entry(5002L, "store_manager"),
            Map.entry(2001L, "photo_a"),
            Map.entry(2002L, "photo_b"),
            Map.entry(3001L, "makeup_a"),
            Map.entry(3002L, "makeup_b"),
            Map.entry(4001L, "selector_a"),
            Map.entry(4002L, "selector_b"),
            Map.entry(1001L, "online_cs"),
            Map.entry(1002L, "online_cs_b"),
            Map.entry(5001L, "clue_manager"),
            Map.entry(1101L, "private_domain"),
            Map.entry(91001L, "finance"));

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

    public String getUserPhone(Long userId) {
        return userId == null ? null : DEFAULT_STAFF_PHONES.get(userId);
    }

    public String getWecomAccount(Long userId) {
        return userId == null ? null : DEFAULT_WECOM_ACCOUNTS.get(userId);
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
