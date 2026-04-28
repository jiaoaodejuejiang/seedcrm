package com.seedcrm.crm.auth.support;

import com.seedcrm.crm.auth.model.AuthMenuNode;
import com.seedcrm.crm.auth.model.AuthenticatedUser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class AuthAccessCatalog {

    private static final List<MenuSeed> MENU_SEEDS = List.of(
            menu(1, "客资中心", "客资列表", "/clues", "CLUE", "clue:view", "ADMIN", "CLUE_MANAGER", "ONLINE_CUSTOMER_SERVICE"),
            menu(2, "客资中心", "顾客排档", "/clues/scheduling", "ORDER", "order:view", "ADMIN", "CLUE_MANAGER", "ONLINE_CUSTOMER_SERVICE"),
            menu(3, "客资中心 / 客资管理", "自动分配", "/clue-management/auto-assign", "CLUE", "clue:assign", "ADMIN", "CLUE_MANAGER"),
            menu(4, "客资中心 / 客资管理", "值班客服", "/clue-management/duty-cs", "CLUE", "clue:update", "ADMIN", "CLUE_MANAGER"),
            menu(5, "客资中心 / 客资管理", "门店档期", "/clue-management/store-schedules", "CLUE", "clue:update", "ADMIN", "CLUE_MANAGER"),
            menu(6, "门店服务", "订单列表", "/store-service/orders", "ORDER", "order:view", "ADMIN", "STORE_SERVICE", "STORE_MANAGER", "PHOTOGRAPHER", "MAKEUP_ARTIST", "PHOTO_SELECTOR"),
            menu(7, "门店服务", "服务单设计", "/store-service/service-design", "PLANORDER", "planorder:update", "ADMIN", "STORE_MANAGER"),
            menu(8, "门店服务", "人员管理", "/store-service/personnel", "SYSTEM", "system:user:update", "ADMIN", "STORE_MANAGER"),
            menu(9, "门店服务", "门店角色", "/store-service/roles", "SYSTEM", "system:role:update", "ADMIN", "STORE_MANAGER"),
            menu(10, "系统设置 / 基础配置", "企业微信", "/settings/base/wecom", "SETTING", "setting:wecom:update", "ADMIN"),
            menu(11, "私域客服", "活码配置", "/private-domain/live-code", "WECOM", "wecom:live-code:update", "ADMIN", "PRIVATE_DOMAIN_SERVICE"),
            menu(12, "私域客服", "客户画像", "/private-domain/customer-profile", "WECOM", "wecom:profile:view", "ADMIN", "PRIVATE_DOMAIN_SERVICE"),
            menu(13, "私域客服", "朋友圈定时群发", "/private-domain/moments", "WECOM", "wecom:moments:update", "ADMIN", "PRIVATE_DOMAIN_SERVICE"),
            menu(14, "私域客服", "标签管理", "/private-domain/tags", "WECOM", "wecom:tags:update", "ADMIN", "PRIVATE_DOMAIN_SERVICE"),
            menu(15, "财务管理", "财务看板", "/finance", "FINANCE", "finance:view", "ADMIN", "FINANCE"),
            menu(16, "财务管理 / 薪酬中心", "我的薪酬", "/finance/salary/my", "SALARY", "salary:view", "ADMIN", "FINANCE", "CLUE_MANAGER", "ONLINE_CUSTOMER_SERVICE", "STORE_SERVICE", "STORE_MANAGER", "PHOTOGRAPHER", "MAKEUP_ARTIST", "PHOTO_SELECTOR", "PRIVATE_DOMAIN_SERVICE"),
            menu(17, "财务管理 / 薪酬结算", "结算单管理", "/finance/salary/settlements", "SALARY", "salary:update", "ADMIN", "FINANCE"),
            menu(18, "财务管理 / 薪酬结算", "结算配置", "/finance/salary/settlement-config", "SALARY", "salary:update", "ADMIN", "FINANCE"),
            menu(19, "财务管理 / 薪酬配置", "薪酬角色", "/finance/salary-config/roles", "SALARY", "salary:update", "ADMIN"),
            menu(20, "财务管理 / 薪酬配置", "薪酬档位", "/finance/salary-config/grades", "SALARY", "salary:update", "ADMIN"),
            menu(21, "财务管理 / 薪酬配置", "分销配置", "/finance/salary-config/distributor", "SALARY", "salary:update", "ADMIN"),
            menu(22, "系统管理", "部门管理", "/system/departments", "SYSTEM", "system:department:update", "ADMIN"),
            menu(23, "系统管理", "员工管理", "/system/employees", "SYSTEM", "system:user:update", "ADMIN", "CLUE_MANAGER"),
            menu(24, "系统管理", "岗位管理", "/system/positions", "SYSTEM", "system:position:update", "ADMIN"),
            menu(25, "系统管理", "角色管理", "/system/roles", "SYSTEM", "system:role:update", "ADMIN"),
            menu(26, "系统设置 / 基础配置", "域名配置", "/settings/base/domain", "SETTING", "setting:domain:update", "ADMIN"),
            menu(27, "系统设置 / 基础配置", "菜单管理", "/settings/menu", "SETTING", "setting:menu:update", "ADMIN"),
            menu(28, "系统设置 / 调度中心", "抖音接口", "/settings/integration/third-party", "SETTING", "setting:douyin:update", "ADMIN"),
            menu(29, "系统设置 / 调度中心", "回调接口", "/settings/integration/callback", "SETTING", "setting:callback:update", "ADMIN"),
            menu(30, "系统设置 / 调度中心", "任务调度", "/settings/integration/jobs", "SETTING", "setting:scheduler:update", "ADMIN"),
            menu(31, "系统设置 / 调度中心", "接口调试", "/settings/integration/debug", "SETTING", "setting:debug:update", "ADMIN"),
            menu(32, "系统设置 / 调度中心", "对外接口", "/settings/integration/public-api", "SETTING", "setting:public-api:update", "ADMIN"),
            menu(33, "系统设置 / 调度中心", "分销接口", "/settings/integration/distribution-api", "SETTING", "setting:distribution-api:update", "ADMIN"),
            menu(34, "系统设置 / 基础配置", "字典管理", "/settings/dictionaries", "SETTING", "setting:dictionary:update", "ADMIN"),
            menu(35, "系统设置 / 基础配置", "参数管理", "/settings/parameters", "SETTING", "setting:parameter:update", "ADMIN"),
            menu(36, "系统设置 / 基础配置", "支付设置", "/settings/payment", "SETTING", "setting:payment:update", "ADMIN"),
            menu(37, "财务管理 / 薪酬结算", "提现处理", "/finance/salary/withdrawals", "SALARY", "salary:update", "ADMIN", "FINANCE"),
            menu(38, "财务管理 / 薪酬结算", "退款冲正", "/finance/salary/refund-adjustments", "SALARY", "salary:update", "ADMIN", "FINANCE")
    );

    private AuthAccessCatalog() {
    }

    public static AuthenticatedUser enrich(AuthenticatedUser user) {
        if (user == null) {
            return null;
        }
        List<MenuSeed> visibleMenus = visibleMenus(user.getRoleCode());
        Set<String> modules = new LinkedHashSet<>();
        if (user.getAllowedModules() != null) {
            modules.addAll(user.getAllowedModules());
        }
        visibleMenus.stream()
                .map(MenuSeed::moduleCode)
                .filter(StringUtils::hasText)
                .forEach(modules::add);

        Set<String> permissions = visibleMenus.stream()
                .map(MenuSeed::permissionCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        modules.stream()
                .filter(StringUtils::hasText)
                .map(module -> module.toLowerCase(Locale.ROOT) + ":view")
                .forEach(permissions::add);

        List<String> menuRoutes = visibleMenus.stream().map(MenuSeed::routePath).toList();
        user.setAllowedModules(List.copyOf(modules));
        user.setMenuRoutes(menuRoutes);
        user.setDefaultRoute(menuRoutes.isEmpty() ? null : menuRoutes.get(0));
        user.setPermissions(List.copyOf(permissions));
        user.setMenuTree(buildMenuTree(visibleMenus));
        return user;
    }

    private static List<MenuSeed> visibleMenus(String roleCode) {
        String normalizedRole = normalize(roleCode);
        return MENU_SEEDS.stream()
                .filter(seed -> seed.roleCodes().contains("ADMIN") && "ADMIN".equals(normalizedRole)
                        || seed.roleCodes().contains(normalizedRole))
                .sorted(Comparator.comparingInt(MenuSeed::sortOrder))
                .toList();
    }

    private static List<AuthMenuNode> buildMenuTree(List<MenuSeed> visibleMenus) {
        Map<String, AuthMenuNode> topNodes = new LinkedHashMap<>();
        for (MenuSeed seed : visibleMenus) {
            String[] groups = seed.menuGroup().split("\\s*/\\s*");
            AuthMenuNode parent = null;
            String pathKey = "";
            for (String rawGroup : groups) {
                String group = rawGroup.trim();
                if (!StringUtils.hasText(group)) {
                    continue;
                }
                pathKey = pathKey.isBlank() ? group : pathKey + "/" + group;
                AuthMenuNode node = parent == null
                        ? topNodes.computeIfAbsent(pathKey, key -> groupNode(key, group))
                        : findOrCreateChildGroup(parent, pathKey, group);
                parent = node;
            }
            AuthMenuNode leaf = new AuthMenuNode(
                    "menu:" + seed.routePath(),
                    seed.menuName(),
                    seed.routePath(),
                    seed.moduleCode(),
                    seed.permissionCode());
            if (parent == null) {
                topNodes.put(leaf.getKey(), leaf);
            } else {
                parent.getChildren().add(leaf);
            }
        }
        return new ArrayList<>(topNodes.values());
    }

    private static AuthMenuNode groupNode(String key, String label) {
        return new AuthMenuNode("group:" + key, label, null, null, null);
    }

    private static AuthMenuNode findOrCreateChildGroup(AuthMenuNode parent, String key, String label) {
        String nodeKey = "group:" + key;
        for (AuthMenuNode child : parent.getChildren()) {
            if (nodeKey.equals(child.getKey())) {
                return child;
            }
        }
        AuthMenuNode child = groupNode(key, label);
        parent.getChildren().add(child);
        return child;
    }

    private static MenuSeed menu(int sortOrder,
                                 String menuGroup,
                                 String menuName,
                                 String routePath,
                                 String moduleCode,
                                 String permissionCode,
                                 String... roleCodes) {
        return new MenuSeed(sortOrder, menuGroup, menuName, routePath, moduleCode, permissionCode,
                Set.of(roleCodes).stream().map(AuthAccessCatalog::normalize).collect(Collectors.toSet()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record MenuSeed(int sortOrder,
                            String menuGroup,
                            String menuName,
                            String routePath,
                            String moduleCode,
                            String permissionCode,
                            Set<String> roleCodes) {
    }
}
