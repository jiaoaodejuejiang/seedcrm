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
}
