package com.seedcrm.crm.auth.service.impl;

import com.seedcrm.crm.auth.dto.SystemAccessMenuResponse;
import com.seedcrm.crm.auth.dto.SystemAccessMenuSaveRequest;
import com.seedcrm.crm.auth.dto.SystemAccessPermissionResponse;
import com.seedcrm.crm.auth.dto.SystemAccessRoleResponse;
import com.seedcrm.crm.auth.dto.SystemAccessRoleSaveRequest;
import com.seedcrm.crm.auth.dto.SystemAccessSnapshotResponse;
import com.seedcrm.crm.auth.service.SystemAccessConfigService;
import com.seedcrm.crm.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemAccessConfigServiceImpl implements SystemAccessConfigService {

    private final JdbcTemplate jdbcTemplate;

    public SystemAccessConfigServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SystemAccessSnapshotResponse snapshot() {
        SystemAccessSnapshotResponse response = new SystemAccessSnapshotResponse();
        response.setMenus(listMenus());
        response.setRoles(listRoles(response.getMenus()));
        response.setPermissions(listPermissions());
        return response;
    }

    @Override
    @Transactional
    public SystemAccessMenuResponse saveMenu(SystemAccessMenuSaveRequest request) {
        validateMenu(request);
        LocalDateTime now = LocalDateTime.now();
        String routePath = normalizeRoute(request.getRoutePath());
        String moduleCode = normalize(request.getModuleCode());
        String permissionCode = normalizePermission(request.getPermissionCode(), moduleCode);
        Integer enabled = request.getIsEnabled() == null ? 1 : request.getIsEnabled();
        Integer sortOrder = request.getSortOrder() == null ? nextMenuSortOrder() : request.getSortOrder();
        String componentKey = StringUtils.hasText(request.getComponentKey()) ? request.getComponentKey().trim() : componentKey(routePath);

        if (request.getId() == null) {
            jdbcTemplate.update("""
                    INSERT INTO sys_menu(menu_group, menu_name, route_path, module_code, permission_code, component_key, menu_type, is_enabled, sort_order, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'MENU', ?, ?, ?, ?)
                    """, request.getMenuGroup().trim(), request.getMenuName().trim(), routePath, moduleCode, permissionCode,
                    componentKey, enabled, sortOrder, now, now);
        } else {
            jdbcTemplate.update("""
                    UPDATE sys_menu
                    SET menu_group = ?,
                        menu_name = ?,
                        route_path = ?,
                        module_code = ?,
                        permission_code = ?,
                        component_key = ?,
                        is_enabled = ?,
                        sort_order = ?,
                        updated_at = ?
                    WHERE id = ?
                    """, request.getMenuGroup().trim(), request.getMenuName().trim(), routePath, moduleCode, permissionCode,
                    componentKey, enabled, sortOrder, now, request.getId());
        }

        Long menuId = menuIdByRoute(routePath);
        seedPermission(permissionCode, moduleCode, request.getMenuName(), sortOrder);
        syncMenuRoles(menuId, request.getRoleCodes(), permissionCode);
        return menuById(menuId);
    }

    @Override
    @Transactional
    public SystemAccessRoleResponse saveRole(SystemAccessRoleSaveRequest request) {
        validateRole(request);
        LocalDateTime now = LocalDateTime.now();
        String roleCode = normalize(request.getRoleCode());
        String dataScope = StringUtils.hasText(request.getDataScope()) ? normalize(request.getDataScope()) : "SELF";
        String roleType = StringUtils.hasText(request.getRoleType()) ? normalize(request.getRoleType()) : "BUSINESS";
        Integer enabled = request.getIsEnabled() == null ? 1 : request.getIsEnabled();
        Integer sortOrder = request.getSortOrder() == null ? nextRoleSortOrder() : request.getSortOrder();

        jdbcTemplate.update("""
                INSERT INTO sys_role(role_code, role_name, data_scope, role_type, is_enabled, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    role_name = VALUES(role_name),
                    data_scope = VALUES(data_scope),
                    role_type = VALUES(role_type),
                    is_enabled = VALUES(is_enabled),
                    sort_order = VALUES(sort_order),
                    updated_at = VALUES(updated_at)
                """, roleCode, request.getRoleName().trim(), dataScope, roleType, enabled, sortOrder, now, now);

        syncRoleMenus(roleCode, request.getMenuRoutes());
        syncRolePermissions(roleCode, request.getPermissionCodes(), request.getMenuRoutes());
        return roleByCode(roleCode, listMenus());
    }

    private List<SystemAccessMenuResponse> listMenus() {
        List<SystemAccessMenuResponse> menus = jdbcTemplate.query("""
                SELECT id, menu_group, menu_name, route_path, module_code, permission_code, component_key, is_enabled, sort_order
                FROM sys_menu
                ORDER BY sort_order ASC, id ASC
                """, (rs, rowNum) -> {
                SystemAccessMenuResponse item = new SystemAccessMenuResponse();
                item.setId(rs.getLong("id"));
                item.setMenuGroup(rs.getString("menu_group"));
                item.setMenuName(rs.getString("menu_name"));
                item.setRoutePath(rs.getString("route_path"));
                item.setModuleCode(rs.getString("module_code"));
                item.setPermissionCode(rs.getString("permission_code"));
                item.setComponentKey(rs.getString("component_key"));
                item.setIsEnabled(rs.getInt("is_enabled"));
                item.setSortOrder(rs.getInt("sort_order"));
                return item;
            });

        Map<Long, List<String>> rolesByMenu = jdbcTemplate.query("""
                SELECT menu_id, role_code
                FROM sys_role_menu
                ORDER BY role_code ASC
                """, rs -> {
                Map<Long, List<String>> result = new java.util.LinkedHashMap<>();
                while (rs.next()) {
                    result.computeIfAbsent(rs.getLong("menu_id"), key -> new ArrayList<>()).add(rs.getString("role_code"));
                }
                return result;
            });
        menus.forEach(menu -> menu.setRoleCodes(rolesByMenu.getOrDefault(menu.getId(), List.of())));
        return menus;
    }

    private List<SystemAccessRoleResponse> listRoles(List<SystemAccessMenuResponse> menus) {
        List<SystemAccessRoleResponse> roles = jdbcTemplate.query("""
                SELECT id, role_code, role_name, data_scope, role_type, is_enabled, sort_order
                FROM sys_role
                ORDER BY sort_order ASC, id ASC
                """, (rs, rowNum) -> {
                SystemAccessRoleResponse item = new SystemAccessRoleResponse();
                item.setId(rs.getLong("id"));
                item.setRoleCode(rs.getString("role_code"));
                item.setRoleName(rs.getString("role_name"));
                item.setDataScope(rs.getString("data_scope"));
                item.setRoleType(rs.getString("role_type"));
                item.setIsEnabled(rs.getInt("is_enabled"));
                item.setSortOrder(rs.getInt("sort_order"));
                return item;
            });

        for (SystemAccessRoleResponse role : roles) {
            fillRoleGrants(role, menus);
        }
        return roles;
    }

    private List<SystemAccessPermissionResponse> listPermissions() {
        return jdbcTemplate.query("""
                SELECT id, permission_code, module_code, action_code, permission_name, is_enabled, sort_order
                FROM sys_permission
                ORDER BY module_code ASC, sort_order ASC, id ASC
                """, (rs, rowNum) -> {
                SystemAccessPermissionResponse item = new SystemAccessPermissionResponse();
                item.setId(rs.getLong("id"));
                item.setPermissionCode(rs.getString("permission_code"));
                item.setModuleCode(rs.getString("module_code"));
                item.setActionCode(rs.getString("action_code"));
                item.setPermissionName(rs.getString("permission_name"));
                item.setIsEnabled(rs.getInt("is_enabled"));
                item.setSortOrder(rs.getInt("sort_order"));
                return item;
            });
    }

    private SystemAccessMenuResponse menuById(Long id) {
        return listMenus().stream()
                .filter(menu -> id.equals(menu.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("menu not found"));
    }

    private SystemAccessRoleResponse roleByCode(String roleCode, List<SystemAccessMenuResponse> menus) {
        return listRoles(menus).stream()
                .filter(role -> roleCode.equalsIgnoreCase(role.getRoleCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("role not found"));
    }

    private void fillRoleGrants(SystemAccessRoleResponse role, List<SystemAccessMenuResponse> menus) {
        String roleCode = normalize(role.getRoleCode());
        List<String> menuRoutes = menus.stream()
                .filter(menu -> menu.getRoleCodes().stream().anyMatch(code -> roleCode.equals(normalize(code))))
                .map(SystemAccessMenuResponse::getRoutePath)
                .toList();
        List<String> permissionCodes = jdbcTemplate.queryForList("""
                SELECT permission_code
                FROM sys_role_permission
                WHERE role_code = ?
                ORDER BY permission_code ASC
                """, String.class, roleCode);
        Set<String> moduleCodes = new LinkedHashSet<>();
        menus.stream()
                .filter(menu -> menuRoutes.contains(menu.getRoutePath()))
                .map(SystemAccessMenuResponse::getModuleCode)
                .filter(StringUtils::hasText)
                .forEach(moduleCodes::add);
        jdbcTemplate.queryForList("""
                SELECT DISTINCT module_code
                FROM sys_permission
                WHERE permission_code IN (%s)
                """.formatted(permissionCodes.isEmpty() ? "''" : permissionCodes.stream().map(code -> "?").collect(Collectors.joining(","))),
                String.class,
                permissionCodes.toArray()).stream().filter(StringUtils::hasText).forEach(moduleCodes::add);

        role.setMenuRoutes(menuRoutes);
        role.setPermissionCodes(permissionCodes);
        role.setModuleCodes(List.copyOf(moduleCodes));
    }

    private void syncMenuRoles(Long menuId, List<String> roleCodes, String permissionCode) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id = ?", menuId);
        LocalDateTime now = LocalDateTime.now();
        for (String roleCode : normalizedList(roleCodes)) {
            jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu(role_code, menu_id, created_at) VALUES (?, ?, ?)", roleCode, menuId, now);
            jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission(role_code, permission_code, created_at) VALUES (?, ?, ?)", roleCode, permissionCode, now);
        }
    }

    private void syncRoleMenus(String roleCode, List<String> menuRoutes) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_code = ?", roleCode);
        LocalDateTime now = LocalDateTime.now();
        for (String routePath : normalizedRoutes(menuRoutes)) {
            Long menuId = menuIdByRoute(routePath);
            jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu(role_code, menu_id, created_at) VALUES (?, ?, ?)", roleCode, menuId, now);
        }
    }

    private void syncRolePermissions(String roleCode, List<String> permissionCodes, List<String> menuRoutes) {
        Set<String> permissions = new LinkedHashSet<>(normalizedPermissionList(permissionCodes));
        for (String routePath : normalizedRoutes(menuRoutes)) {
            String permissionCode = jdbcTemplate.queryForObject(
                    "SELECT permission_code FROM sys_menu WHERE route_path = ? LIMIT 1",
                    String.class,
                    routePath);
            if (StringUtils.hasText(permissionCode)) {
                permissions.add(permissionCode);
            }
        }

        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_code = ?", roleCode);
        LocalDateTime now = LocalDateTime.now();
        for (String permissionCode : permissions) {
            jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission(role_code, permission_code, created_at) VALUES (?, ?, ?)", roleCode, permissionCode, now);
        }
    }

    private void seedPermission(String permissionCode, String moduleCode, String permissionName, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission(permission_code, module_code, action_code, permission_name, is_enabled, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    module_code = VALUES(module_code),
                    action_code = VALUES(action_code),
                    permission_name = VALUES(permission_name),
                    sort_order = VALUES(sort_order),
                    updated_at = VALUES(updated_at)
                """, permissionCode, moduleCode, actionCode(permissionCode), permissionName, sortOrder, LocalDateTime.now(), LocalDateTime.now());
    }

    private Long menuIdByRoute(String routePath) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_menu WHERE route_path = ? LIMIT 1", Long.class, routePath);
    }

    private int nextMenuSortOrder() {
        Integer max = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(sort_order), 0) FROM sys_menu", Integer.class);
        return (max == null ? 0 : max) + 10;
    }

    private int nextRoleSortOrder() {
        Integer max = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(sort_order), 0) FROM sys_role", Integer.class);
        return (max == null ? 0 : max) + 10;
    }

    private void validateMenu(SystemAccessMenuSaveRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getMenuGroup())
                || !StringUtils.hasText(request.getMenuName())
                || !StringUtils.hasText(request.getRoutePath())
                || !StringUtils.hasText(request.getModuleCode())) {
            throw new BusinessException("menuGroup, menuName, routePath and moduleCode are required");
        }
    }

    private void validateRole(SystemAccessRoleSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getRoleCode()) || !StringUtils.hasText(request.getRoleName())) {
            throw new BusinessException("roleCode and roleName are required");
        }
    }

    private List<String> normalizedList(List<String> values) {
        return values == null ? List.of() : values.stream().map(this::normalize).filter(StringUtils::hasText).distinct().toList();
    }

    private List<String> normalizedPermissionList(List<String> values) {
        return values == null ? List.of() : values.stream().map(this::normalizePermissionCode).filter(StringUtils::hasText).distinct().toList();
    }

    private List<String> normalizedRoutes(List<String> values) {
        return values == null ? List.of() : values.stream().map(this::normalizeRoute).filter(StringUtils::hasText).distinct().toList();
    }

    private String normalizeRoute(String routePath) {
        String value = routePath == null ? "" : routePath.trim();
        return value.startsWith("/") ? value : "/" + value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePermission(String permissionCode, String moduleCode) {
        if (StringUtils.hasText(permissionCode)) {
            return normalizePermissionCode(permissionCode);
        }
        return moduleCode.toLowerCase(Locale.ROOT) + ":view";
    }

    private String normalizePermissionCode(String permissionCode) {
        return permissionCode == null ? "" : permissionCode.trim().toLowerCase(Locale.ROOT);
    }

    private String actionCode(String permissionCode) {
        int splitIndex = permissionCode.indexOf(':');
        if (splitIndex < 0 || splitIndex == permissionCode.length() - 1) {
            return permissionCode.toUpperCase(Locale.ROOT);
        }
        return permissionCode.substring(splitIndex + 1).toUpperCase(Locale.ROOT);
    }

    private String componentKey(String routePath) {
        return routePath == null ? "" : routePath.replaceAll("^/+", "").replace('/', '.');
    }
}
