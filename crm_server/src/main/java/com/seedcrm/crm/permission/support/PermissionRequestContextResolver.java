package com.seedcrm.crm.permission.support;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PermissionRequestContextResolver {

    public PermissionRequestContext resolve(HttpServletRequest request) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(normalize(stringHeader(request, "X-Role-Code", "roleCode", "ADMIN")));
        context.setDataScope(normalize(stringHeader(request, "X-Data-Scope", "dataScope", null)));
        context.setCurrentUserId(longHeader(request, "X-User-Id", "userId"));
        context.setCurrentStoreId(longHeader(request, "X-Store-Id", "storeId"));
        context.setResourceStoreId(longHeader(request, "X-Resource-Store-Id", "resourceStoreId"));
        context.setBoundCustomerUserId(longHeader(request, "X-Bound-Customer-User-Id", "boundCustomerUserId"));
        context.setTeamMemberIds(parseLongList(stringHeader(request, "X-Team-Member-Ids", "teamMemberIds", null)));
        return context;
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
