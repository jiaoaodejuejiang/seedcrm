package com.seedcrm.crm.auth.support;

import com.seedcrm.crm.auth.model.AuthenticatedUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class AuthAccessConfigService implements AuthAccessProvider {

    private final JdbcTemplate jdbcTemplate;

    public AuthAccessConfigService(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public AuthenticatedUser enrich(AuthenticatedUser user) {
        if (user == null) {
            return null;
        }

        String roleCode = normalize(user.getRoleCode());
        try {
            RoleRuntime role = findRole(roleCode);
            if (role == null) {
                log.warn("sys_role missing for roleCode={}, clear access to avoid permission fallback expansion", roleCode);
                return clearAccess(user);
            }
            if (!role.enabled()) {
                return clearAccess(user);
            }

            List<AuthAccessCatalog.MenuSeed> menus = findVisibleMenus(role.roleCode());
            user.setAllowedModules(List.of());
            AuthAccessCatalog.enrich(user, menus);
            applyRolePermissions(user, role.roleCode());
            applyPermissionPolicies(user, role.roleCode());
            return user;
        } catch (DataAccessException ex) {
            log.warn("failed to load auth access config from database, fallback to AuthAccessCatalog, roleCode={}", roleCode, ex);
            return AuthAccessCatalog.enrich(user);
        }
    }

    private RoleRuntime findRole(String roleCode) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT role_code, is_enabled
                    FROM sys_role
                    WHERE role_code = ?
                    LIMIT 1
                    """, (rs, rowNum) -> new RoleRuntime(
                    normalize(rs.getString("role_code")),
                    rs.getInt("is_enabled") == 1), roleCode);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private List<AuthAccessCatalog.MenuSeed> findVisibleMenus(String roleCode) {
        return jdbcTemplate.query("""
                SELECT m.sort_order,
                       m.menu_group,
                       m.menu_name,
                       m.route_path,
                       m.module_code,
                       m.permission_code
                FROM sys_menu m
                INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
                WHERE rm.role_code = ?
                  AND m.is_enabled = 1
                ORDER BY m.sort_order ASC, m.id ASC
                """, (rs, rowNum) -> new AuthAccessCatalog.MenuSeed(
                rs.getInt("sort_order"),
                rs.getString("menu_group"),
                rs.getString("menu_name"),
                rs.getString("route_path"),
                normalize(rs.getString("module_code")),
                rs.getString("permission_code"),
                Set.of(roleCode)), roleCode);
    }

    private void applyRolePermissions(AuthenticatedUser user, String roleCode) {
        List<PermissionRuntime> rolePermissions = jdbcTemplate.query("""
                SELECT p.permission_code, p.module_code
                FROM sys_permission p
                INNER JOIN sys_role_permission rp ON rp.permission_code = p.permission_code
                WHERE rp.role_code = ?
                  AND p.is_enabled = 1
                ORDER BY p.module_code ASC, p.permission_code ASC
                """, (rs, rowNum) -> new PermissionRuntime(
                rs.getString("permission_code"),
                normalize(rs.getString("module_code"))), roleCode);

        Set<String> permissions = new LinkedHashSet<>(user.getPermissions() == null ? List.of() : user.getPermissions());
        Set<String> modules = new LinkedHashSet<>(user.getAllowedModules() == null ? List.of() : user.getAllowedModules());

        for (PermissionRuntime permission : rolePermissions) {
            if (StringUtils.hasText(permission.permissionCode())) {
                permissions.add(permission.permissionCode());
            }
            if (StringUtils.hasText(permission.moduleCode())) {
                modules.add(permission.moduleCode());
            }
        }

        user.setPermissions(List.copyOf(permissions));
        user.setAllowedModules(List.copyOf(modules));
    }

    private void applyPermissionPolicies(AuthenticatedUser user, String roleCode) {
        try {
            List<PermissionRuntime> policyPermissions = jdbcTemplate.query("""
                    SELECT module_code, action_code
                    FROM permission_policy
                    WHERE role_code = ?
                      AND is_enabled = 1
                    ORDER BY module_code ASC, action_code ASC
                    """, (rs, rowNum) -> {
                    String moduleCode = normalize(rs.getString("module_code"));
                    String actionCode = normalize(rs.getString("action_code")).toLowerCase(Locale.ROOT);
                    return new PermissionRuntime(moduleCode.toLowerCase(Locale.ROOT) + ":" + actionCode, moduleCode);
                }, roleCode);

            Set<String> permissions = new LinkedHashSet<>(user.getPermissions() == null ? List.of() : user.getPermissions());
            Set<String> modules = new LinkedHashSet<>(user.getAllowedModules() == null ? List.of() : user.getAllowedModules());

            for (PermissionRuntime permission : policyPermissions) {
                if (StringUtils.hasText(permission.permissionCode())) {
                    permissions.add(permission.permissionCode());
                }
                if (StringUtils.hasText(permission.moduleCode())) {
                    modules.add(permission.moduleCode());
                }
            }

            user.setPermissions(List.copyOf(permissions));
            user.setAllowedModules(List.copyOf(modules));
        } catch (DataAccessException ex) {
            log.warn("permission_policy unavailable while enriching auth access, roleCode={}", roleCode, ex);
        }
    }

    private AuthenticatedUser clearAccess(AuthenticatedUser user) {
        user.setAllowedModules(List.of());
        user.setMenuRoutes(List.of());
        user.setMenuTree(List.of());
        user.setPermissions(List.of());
        user.setDefaultRoute(null);
        return user;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record RoleRuntime(String roleCode, boolean enabled) {
    }

    private record PermissionRuntime(String permissionCode, String moduleCode) {
    }
}
