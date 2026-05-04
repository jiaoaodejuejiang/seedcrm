package com.seedcrm.crm.auth.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthAccessSchemaInitializerTest {

    @Test
    void customerSchedulingShouldStayInClueCenterAndRejectStoreMenuGrant() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:auth_access;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        AuthAccessSchemaInitializer initializer = new AuthAccessSchemaInitializer(dataSource);
        initializer.initialize();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long menuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE route_path = '/clues/scheduling'",
                Long.class);
        String menuGroup = jdbcTemplate.queryForObject(
                "SELECT menu_group FROM sys_menu WHERE id = ?",
                String.class,
                menuId);
        assertThat(menuGroup).isEqualTo("客资中心 / 线上客服工作台");

        jdbcTemplate.update("INSERT INTO sys_role_menu(role_code, menu_id) VALUES ('STORE_MANAGER', ?)", menuId);
        initializer.initialize();

        Integer storeGrantCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_menu
                WHERE menu_id = ?
                  AND role_code IN ('STORE_SERVICE', 'STORE_MANAGER', 'PHOTOGRAPHER', 'MAKEUP_ARTIST', 'PHOTO_SELECTOR')
                """, Integer.class, menuId);
        assertThat(storeGrantCount).isZero();

        Integer onlineGrantCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_menu
                WHERE menu_id = ?
                  AND role_code IN ('ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE')
                """, Integer.class, menuId);
        assertThat(onlineGrantCount).isEqualTo(3);
    }
}
