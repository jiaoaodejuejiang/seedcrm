package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.auth.model.AuthenticatedUser;
import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PermissionRequestContextResolver {

    private final AuthService authService;
    private final Environment environment;

    public PermissionRequestContextResolver(AuthService authService, Environment environment) {
        this.authService = authService;
        this.environment = environment;
    }

    public PermissionRequestContext resolve(HttpServletRequest request) {
        AuthenticatedUser authenticatedUser = resolveAuthenticatedUser(request);
        if (authenticatedUser != null) {
            PermissionRequestContext context = new PermissionRequestContext();
            context.setRoleCode(normalize(authenticatedUser.getRoleCode()));
            context.setDataScope(normalize(authenticatedUser.getDataScope()));
            context.setCurrentUserId(authenticatedUser.getUserId());
            context.setCurrentStoreId(authenticatedUser.getStoreId());
            context.setCurrentStoreName(authenticatedUser.getStoreName());
            context.setResourceStoreId(authenticatedUser.getStoreId());
            context.setBoundCustomerUserId(authenticatedUser.getBoundCustomerUserId());
            context.setTeamMemberIds(authenticatedUser.getTeamMemberIds() == null ? List.of() : authenticatedUser.getTeamMemberIds());
            return context;
        }
        PermissionRequestContext context = new PermissionRequestContext();
        if (!allowHeaderFallback()) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
        context.setRoleCode(normalize(stringHeader(request, "X-Role-Code", "roleCode", null)));
        context.setDataScope(normalize(stringHeader(request, "X-Data-Scope", "dataScope", null)));
        context.setCurrentUserId(longHeader(request, "X-User-Id", "userId"));
        context.setCurrentStoreId(longHeader(request, "X-Store-Id", "storeId"));
        context.setCurrentStoreName(stringHeader(request, "X-Store-Name", "storeName", null));
        context.setResourceStoreId(longHeader(request, "X-Resource-Store-Id", "resourceStoreId"));
        context.setBoundCustomerUserId(longHeader(request, "X-Bound-Customer-User-Id", "boundCustomerUserId"));
        context.setTeamMemberIds(parseLongList(stringHeader(request, "X-Team-Member-Ids", "teamMemberIds", null)));
        return context;
    }

    private boolean allowHeaderFallback() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("local") || profile.equals("test") || profile.equals("dev"));
    }

    private AuthenticatedUser resolveAuthenticatedUser(HttpServletRequest request) {
        String token = request.getHeader("X-Auth-Token");
        if (!StringUtils.hasText(token)) {
            token = request.getHeader("Authorization");
            if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        return authService.resolve(token).orElse(null);
    }

    private String stringHeader(HttpServletRequest request, String headerName, String parameterName, String defaultValue) {
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value)) {
            value = request.getParameter(parameterName);
        }
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private Long longHeader(HttpServletRequest request, String headerName, String parameterName) {
        String value = stringHeader(request, headerName, parameterName, null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<Long> parseLongList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Long.valueOf(value);
                    } catch (NumberFormatException exception) {
                        return null;
                    }
                })
                .filter(value -> value != null)
                .collect(Collectors.toList());
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
