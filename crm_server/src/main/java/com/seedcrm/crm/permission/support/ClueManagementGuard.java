package com.seedcrm.crm.permission.support;

import com.seedcrm.crm.common.exception.BusinessException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ClueManagementGuard {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "CLUE_MANAGER");

    public void checkManage(PermissionRequestContext context) {
        String roleCode = normalize(context == null ? null : context.getRoleCode());
        if (!ALLOWED_ROLES.contains(roleCode)) {
            throw new BusinessException("clue management denied: role is not allowed");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
