package com.seedcrm.crm.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seedcrm.crm.common.exception.BusinessException;
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
                .contains("/settings/menu", "/settings/integration/third-party", "/system/roles");
        assertThat(user.getPermissions()).contains("setting:menu:update", "system:role:update");
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
}
