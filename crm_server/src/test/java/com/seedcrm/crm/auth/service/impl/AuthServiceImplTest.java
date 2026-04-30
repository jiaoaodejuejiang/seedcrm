package com.seedcrm.crm.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seedcrm.crm.auth.model.AuthMenuNode;
import com.seedcrm.crm.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthServiceImplTest {

    private final AuthServiceImpl authService = new AuthServiceImpl();

    @Test
    void listStoreOptionsShouldReturnStoreAccounts() {
        var storeOptions = authService.listStoreOptions();

        assertThat(storeOptions).isNotEmpty();
        assertThat(storeOptions.get(0).getStoreId()).isEqualTo(10L);
        assertThat(storeOptions.get(0).getAccounts())
                .extracting(account -> account.getUsername())
                .contains("store_service", "store_manager", "photo_a", "makeup_a", "selector_a");
    }

    @Test
    void storeLoginShouldRequireStoreSelection() {
        assertThatThrownBy(() -> authService.login("store_service", "123456", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("先选择门店");
    }

    @Test
    void storeLoginShouldWriteSelectedStoreIntoSessionContext() {
        String token = authService.login("store_service", "123456", 10L, "静安门店");

        assertThat(authService.getUserOrThrow(token).getStoreId()).isEqualTo(10L);
        assertThat(authService.getUserOrThrow(token).getStoreName()).isEqualTo("静安门店");
    }

    @Test
    void privateDomainLoginShouldReceiveOnlyPrivateDomainAndSelfSalaryMenus() {
        String token = authService.login("private_domain", "123456", null, null);

        var user = authService.getUserOrThrow(token);

        assertThat(user.getDefaultRoute()).isEqualTo("/private-domain/live-code");
        assertThat(user.getMenuRoutes())
                .contains("/private-domain/live-code", "/finance/salary/my")
                .doesNotContain("/settings/menu", "/store-service/orders");
        assertThat(user.getAllowedModules()).contains("WECOM", "SALARY");
        assertThat(user.getPermissions()).contains("wecom:live-code:update", "salary:view");
        assertThat(user.getMenuTree()).hasSize(2);
    }

    @Test
    void adminLoginShouldReceiveSystemConfigurationMenus() {
        String token = authService.login("admin", "123456", null, null);

        var user = authService.getUserOrThrow(token);

        assertThat(user.getDefaultRoute()).isEqualTo("/clues");
        assertThat(user.getMenuRoutes())
                .contains("/settings/menu", "/settings/integration/third-party", "/settings/system-flow", "/system/roles");
        assertThat(user.getPermissions()).contains("setting:menu:update", "setting:system-flow:view", "system:role:update");
    }

    @Test
    void integrationAdminShouldReceiveConfigAndSchedulerPermissions() {
        String token = authService.login("integration_admin", "123456", null, null);

        var user = authService.getUserOrThrow(token);

        assertThat(user.getDefaultRoute()).isEqualTo("/settings/integration/third-party");
        assertThat(user.getMenuRoutes())
                .contains("/settings/integration/third-party", "/settings/integration/distribution-api", "/settings/integration/jobs");
        assertThat(user.getAllowedModules()).contains("SETTING", "SCHEDULER");
        assertThat(user.getPermissions()).contains("scheduler:view", "setting:distribution-api:update");
    }

    @Test
    void integrationOperatorShouldReceiveMonitorAndRetryMenusWithoutConfigMenu() {
        String token = authService.login("integration_operator", "123456", null, null);

        var user = authService.getUserOrThrow(token);

        assertThat(user.getDefaultRoute()).isEqualTo("/settings/integration/callback");
        assertThat(user.getMenuRoutes())
                .contains("/settings/integration/callback", "/settings/integration/debug", "/settings/integration/distribution-api")
                .doesNotContain("/settings/integration/third-party", "/settings/integration/public-api");
        assertThat(user.getAllowedModules()).contains("SETTING", "SCHEDULER");
        assertThat(user.getPermissions()).contains("scheduler:view", "setting:distribution-api:update");
        assertThat(user.getPermissions()).doesNotContain("scheduler:update");
    }

    @Test
    void partnerAppLoginShouldCarryPartnerScopeWithoutBackendMenus() {
        String token = authService.login("partner_app", "123456", null, null);

        var user = authService.getUserOrThrow(token);

        assertThat(user.getRoleCode()).isEqualTo("PARTNER_APP");
        assertThat(user.getDataScope()).isEqualTo("PARTNER");
        assertThat(user.getPartnerCode()).isEqualTo("DISTRIBUTION");
        assertThat(user.getAllowedModules()).contains("SCHEDULER");
        assertThat(user.getPermissions()).contains("scheduler:view");
        assertThat(user.getMenuRoutes()).isEmpty();
        assertThat(user.getDefaultRoute()).isNull();
    }

    @Test
    void storeStaffShouldStartFromStoreOrderWorkspace() {
        String token = authService.login("store_service", "123456", 10L, "静安门店");

        var user = authService.getUserOrThrow(token);

        assertThat(user.getDefaultRoute()).isEqualTo("/store-service/orders");
        assertThat(user.getMenuRoutes())
                .contains("/store-service/orders", "/finance/salary/my")
                .doesNotContain("/clues", "/settings/menu");
        assertThat(user.getAllowedModules()).contains("ORDER", "PLANORDER", "SALARY");
    }

    @Test
    void loginShouldPreferInjectedAccessProvider() {
        AuthServiceImpl service = new AuthServiceImpl(user -> {
            user.setAllowedModules(List.of("CUSTOM"));
            user.setMenuRoutes(List.of("/custom/workspace"));
            user.setDefaultRoute("/custom/workspace");
            user.setPermissions(List.of("custom:view"));
            user.setMenuTree(List.of(new AuthMenuNode(
                    "menu:/custom/workspace",
                    "自定义工作台",
                    "/custom/workspace",
                    "CUSTOM",
                    "custom:view")));
            return user;
        });

        String token = service.login("private_domain", "123456", null, null);
        var user = service.getUserOrThrow(token);

        assertThat(user.getDefaultRoute()).isEqualTo("/custom/workspace");
        assertThat(user.getMenuRoutes()).containsExactly("/custom/workspace");
        assertThat(user.getAllowedModules()).containsExactly("CUSTOM");
        assertThat(user.getPermissions()).containsExactly("custom:view");
    }
}
