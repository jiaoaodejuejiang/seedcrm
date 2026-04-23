package com.seedcrm.crm.auth.service.impl;

import com.seedcrm.crm.auth.model.AuthenticatedUser;
import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.common.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Map<String, DemoAccount> ACCOUNT_CATALOG = Map.of(
            "admin", new DemoAccount("admin", "123456", new AuthenticatedUser(
                    "admin", "系统管理员", "ADMIN", "管理员", "ALL", 1L, 10L,
                    List.of(1001L, 1002L, 2001L, 2002L, 3001L, 3002L), null,
                    List.of("CLUE", "ORDER", "PLANORDER", "SCHEDULER", "PERMISSION", "SALARY", "DISTRIBUTOR", "FINANCE"))),
            "clue_manager", new DemoAccount("clue_manager", "123456", new AuthenticatedUser(
                    "clue_manager", "客资主管", "CLUE_MANAGER", "客资主管", "ALL", 5001L, 10L,
                    List.of(1001L, 1002L), null, List.of("CLUE", "SCHEDULER"))),
            "online_cs", new DemoAccount("online_cs", "123456", new AuthenticatedUser(
                    "online_cs", "在线客服", "ONLINE_CUSTOMER_SERVICE", "在线客服", "TEAM", 1001L, 10L,
                    List.of(1001L, 1002L), null, List.of("CLUE"))),
            "store_service", new DemoAccount("store_service", "123456", new AuthenticatedUser(
                    "store_service", "门店服务", "STORE_SERVICE", "门店服务", "STORE", 1001L, 10L,
                    List.of(1001L, 1002L, 2001L, 2002L, 3001L, 3002L), null, List.of("ORDER", "PLANORDER"))),
            "finance", new DemoAccount("finance", "123456", new AuthenticatedUser(
                    "finance", "财务", "FINANCE", "财务", "ALL", 91001L, 10L,
                    List.of(91001L), null, List.of("ORDER", "SALARY", "FINANCE"))),
            "private_domain", new DemoAccount("private_domain", "123456", new AuthenticatedUser(
                    "private_domain", "私域服务", "PRIVATE_DOMAIN_SERVICE", "私域服务", "SELF", 1001L, 10L,
                    List.of(1001L), 1001L, List.of("ORDER", "PLANORDER"))));

    private final Map<String, AuthenticatedUser> sessions = new ConcurrentHashMap<>();

    @Override
    public String login(String username, String password) {
        DemoAccount account = ACCOUNT_CATALOG.get(normalize(username).toLowerCase(Locale.ROOT));
        if (account == null || !account.password().equals(password)) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, copy(account.user()));
        return token;
    }

    @Override
    public Optional<AuthenticatedUser> resolve(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        AuthenticatedUser user = sessions.get(token.trim());
        return user == null ? Optional.empty() : Optional.of(copy(user));
    }

    @Override
    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            sessions.remove(token.trim());
        }
    }

    @Override
    public AuthenticatedUser getUserOrThrow(String token) {
        return resolve(token).orElseThrow(() -> new BusinessException("登录状态已失效，请重新登录"));
    }

    @Override
    public Long resolveStoreId(Long userId) {
        if (userId == null) {
            return null;
        }
        return ACCOUNT_CATALOG.values().stream()
                .map(DemoAccount::user)
                .filter(user -> userId.equals(user.getUserId()))
                .map(AuthenticatedUser::getStoreId)
                .findFirst()
                .orElse(10L);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private AuthenticatedUser copy(AuthenticatedUser source) {
        return new AuthenticatedUser(
                source.getUsername(),
                source.getDisplayName(),
                source.getRoleCode(),
                source.getRoleName(),
                source.getDataScope(),
                source.getUserId(),
                source.getStoreId(),
                source.getTeamMemberIds() == null ? List.of() : List.copyOf(source.getTeamMemberIds()),
                source.getBoundCustomerUserId(),
                source.getAllowedModules() == null ? List.of() : List.copyOf(source.getAllowedModules()));
    }

    private record DemoAccount(String username, String password, AuthenticatedUser user) {
    }
}
