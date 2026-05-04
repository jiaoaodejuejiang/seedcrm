package com.seedcrm.crm.auth.support;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthAccessSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public AuthAccessSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        createTables();
        seedDefaults();
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_role (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    role_code VARCHAR(64) NOT NULL,
                    role_name VARCHAR(100) NOT NULL,
                    data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF',
                    role_type VARCHAR(32) NOT NULL DEFAULT 'BUSINESS',
                    is_enabled TINYINT DEFAULT 1,
                    sort_order INT DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_sys_role_code (role_code)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_menu (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    menu_group VARCHAR(128) NOT NULL,
                    menu_name VARCHAR(100) NOT NULL,
                    route_path VARCHAR(255) NOT NULL,
                    module_code VARCHAR(64) NOT NULL,
                    permission_code VARCHAR(128) NOT NULL,
                    component_key VARCHAR(128),
                    menu_type VARCHAR(32) NOT NULL DEFAULT 'MENU',
                    is_enabled TINYINT DEFAULT 1,
                    sort_order INT DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_sys_menu_route_path (route_path)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_role_menu (
                    role_code VARCHAR(64) NOT NULL,
                    menu_id BIGINT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (role_code, menu_id),
                    KEY idx_sys_role_menu_menu_id (menu_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_permission (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    permission_code VARCHAR(128) NOT NULL,
                    module_code VARCHAR(64) NOT NULL,
                    action_code VARCHAR(96) NOT NULL,
                    permission_name VARCHAR(100) NOT NULL,
                    is_enabled TINYINT DEFAULT 1,
                    sort_order INT DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_sys_permission_code (permission_code)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_role_permission (
                    role_code VARCHAR(64) NOT NULL,
                    permission_code VARCHAR(128) NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (role_code, permission_code)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_audit_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    actor_user_id BIGINT,
                    actor_username VARCHAR(64),
                    action_code VARCHAR(64) NOT NULL,
                    target_type VARCHAR(64) NOT NULL,
                    target_key VARCHAR(128),
                    before_value TEXT,
                    after_value TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void seedDefaults() {
        AuthAccessCatalog.roleSeeds().forEach(this::seedRole);
        AuthAccessCatalog.menuSeeds().forEach(this::seedMenuAndGrants);
        enforceCustomerSchedulingMenuBoundary();
        log.info("auth access system tables initialized with {} menu seeds", AuthAccessCatalog.menuSeeds().size());
    }

    private void seedRole(AuthAccessCatalog.RoleSeed role) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO sys_role(role_code, role_name, data_scope, role_type, is_enabled, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    role_name = VALUES(role_name),
                    data_scope = VALUES(data_scope),
                    role_type = VALUES(role_type),
                    sort_order = VALUES(sort_order),
                    updated_at = VALUES(updated_at)
                """, role.roleCode(), role.roleName(), role.dataScope(), role.roleType(), role.sortOrder(), now, now);
    }

    private void seedMenuAndGrants(AuthAccessCatalog.MenuSeed menu) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO sys_menu(menu_group, menu_name, route_path, module_code, permission_code, component_key, menu_type, is_enabled, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'MENU', 1, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    menu_group = VALUES(menu_group),
                    menu_name = VALUES(menu_name),
                    module_code = VALUES(module_code),
                    permission_code = VALUES(permission_code),
                    component_key = VALUES(component_key),
                    sort_order = VALUES(sort_order),
                    updated_at = VALUES(updated_at)
                """, menu.menuGroup(), menu.menuName(), menu.routePath(), menu.moduleCode(), menu.permissionCode(),
                componentKey(menu.routePath()), menu.sortOrder(), now, now);

        Long menuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE route_path = ? LIMIT 1",
                Long.class,
                menu.routePath());

        seedPermission(menu.permissionCode(), menu.moduleCode(), menu.menuName(), menu.sortOrder());
        for (String roleCode : menu.roleCodes()) {
            jdbcTemplate.update("""
                    INSERT IGNORE INTO sys_role_menu(role_code, menu_id, created_at)
                    VALUES (?, ?, ?)
                    """, roleCode, menuId, now);
            jdbcTemplate.update("""
                    INSERT IGNORE INTO sys_role_permission(role_code, permission_code, created_at)
                    VALUES (?, ?, ?)
                    """, roleCode, menu.permissionCode(), now);
        }
    }

    private void seedPermission(String permissionCode, String moduleCode, String permissionName, int sortOrder) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO sys_permission(permission_code, module_code, action_code, permission_name, is_enabled, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    module_code = VALUES(module_code),
                    action_code = VALUES(action_code),
                    permission_name = VALUES(permission_name),
                    sort_order = VALUES(sort_order),
                    updated_at = VALUES(updated_at)
                """, permissionCode, moduleCode, actionCode(permissionCode), permissionName, sortOrder, now, now);
    }

    private void enforceCustomerSchedulingMenuBoundary() {
        jdbcTemplate.update("""
                DELETE FROM sys_role_menu
                WHERE role_code IN ('STORE_SERVICE', 'STORE_MANAGER', 'PHOTOGRAPHER', 'MAKEUP_ARTIST', 'PHOTO_SELECTOR')
                  AND menu_id IN (
                      SELECT id FROM sys_menu WHERE route_path = '/clues/scheduling'
                  )
                """);
    }

    private String actionCode(String permissionCode) {
        int splitIndex = permissionCode.indexOf(':');
        if (splitIndex < 0 || splitIndex == permissionCode.length() - 1) {
            return permissionCode.toUpperCase();
        }
        return permissionCode.substring(splitIndex + 1).toUpperCase();
    }

    private String componentKey(String routePath) {
        return routePath == null ? "" : routePath.replaceAll("^/+", "").replace('/', '.');
    }
}
