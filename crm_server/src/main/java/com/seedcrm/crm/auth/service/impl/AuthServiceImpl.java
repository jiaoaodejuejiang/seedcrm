package com.seedcrm.crm.auth.service.impl;

import com.seedcrm.crm.auth.dto.AuthStoreOptionResponse;
import com.seedcrm.crm.auth.model.AuthenticatedUser;
import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.common.exception.BusinessException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthServiceImpl implements AuthService {

    private static final List<String> STORE_ROLE_CODES = List.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");

    private static final Map<String, DemoAccount> ACCOUNT_CATALOG = Map.of(
            "admin", new DemoAccount("admin", "123456", new AuthenticatedUser(
                    "admin", "系统管理员", "ADMIN", "管理员", "ALL", 1L, 10L, "总部",
                    List.of(1001L, 1002L, 2001L, 2002L, 3001L, 3002L), null,
                    List.of("CLUE", "ORDER", "PLANORDER", "SALARY", "FINANCE", "SYSTEM", "SETTING", "WECOM"))),
            "clue_manager", new DemoAccount("clue_manager", "123456", new AuthenticatedUser(
                    "clue_manager", "客资主管", "CLUE_MANAGER", "客资主管", "ALL", 5001L, 10L, "总部",
                    List.of(1001L, 1002L), null, List.of("CLUE", "ORDER", "SYSTEM"))),
            "online_cs", new DemoAccount("online_cs", "123456", new AuthenticatedUser(
                    "online_cs", "在线客服", "ONLINE_CUSTOMER_SERVICE", "在线客服", "TEAM", 1001L, 10L, "总部",
                    List.of(1001L, 1002L), null, List.of("CLUE", "ORDER"))),
            "store_service", new DemoAccount("store_service", "123456", new AuthenticatedUser(
                    "store_service", "门店服务A", "STORE_SERVICE", "门店服务", "STORE", 5101L, 10L, "静安门店",
                    List.of(5101L, 5002L, 2001L, 2002L, 3001L, 3002L, 4001L, 4002L), null, List.of("ORDER", "PLANORDER", "SALARY"))),
            "store_manager", new DemoAccount("store_manager", "123456", new AuthenticatedUser(
                    "store_manager", "静安店长", "STORE_MANAGER", "店长", "STORE", 5002L, 10L, "静安门店",
                    List.of(5101L, 5002L, 2001L, 2002L, 3001L, 3002L, 4001L, 4002L), null, List.of("ORDER", "PLANORDER", "SYSTEM", "SALARY"))),
            "photo_a", new DemoAccount("photo_a", "123456", new AuthenticatedUser(
                    "photo_a", "摄影A", "PHOTOGRAPHER", "摄影", "STORE", 2001L, 10L, "静安门店",
                    List.of(5101L, 5002L, 2001L, 2002L, 3001L, 3002L, 4001L, 4002L), null, List.of("ORDER", "PLANORDER", "SALARY"))),
            "makeup_a", new DemoAccount("makeup_a", "123456", new AuthenticatedUser(
                    "makeup_a", "化妆师A", "MAKEUP_ARTIST", "化妆师", "STORE", 3001L, 10L, "静安门店",
                    List.of(5101L, 5002L, 2001L, 2002L, 3001L, 3002L, 4001L, 4002L), null, List.of("ORDER", "PLANORDER", "SALARY"))),
            "selector_a", new DemoAccount("selector_a", "123456", new AuthenticatedUser(
                    "selector_a", "选片负责人A", "PHOTO_SELECTOR", "选片负责人", "STORE", 4001L, 10L, "静安门店",
                    List.of(5101L, 5002L, 2001L, 2002L, 3001L, 3002L, 4001L, 4002L), null, List.of("ORDER", "PLANORDER", "SALARY"))),
            "finance", new DemoAccount("finance", "123456", new AuthenticatedUser(
                    "finance", "财务", "FINANCE", "财务", "ALL", 91001L, 10L, "总部",
                    List.of(91001L), null, List.of("ORDER", "SALARY", "FINANCE"))),
            "private_domain", new DemoAccount("private_domain", "123456", new AuthenticatedUser(
                    "private_domain", "私域客服", "PRIVATE_DOMAIN_SERVICE", "私域客服", "SELF", 1001L, 10L, "总部",
                    List.of(1001L), 1001L, List.of("WECOM"))));

    private final Map<String, AuthenticatedUser> sessions = new ConcurrentHashMap<>();

    @Override
    public String login(String username, String password, Long storeId, String storeName) {
        DemoAccount account = ACCOUNT_CATALOG.get(normalize(username).toLowerCase(Locale.ROOT));
        if (account == null || !account.password().equals(password)) {
            throw new BusinessException("用户名或密码错误");
        }

        AuthenticatedUser sessionUser = copy(account.user());
        if (isStoreAccount(account.user())) {
            validateStoreSelection(account.user(), storeId, storeName);
            sessionUser.setStoreId(storeId != null ? storeId : account.user().getStoreId());
            sessionUser.setStoreName(account.user().getStoreName());
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, sessionUser);
        return token;
    }

    @Override
    public List<AuthStoreOptionResponse> listStoreOptions() {
        return ACCOUNT_CATALOG.values().stream()
                .map(DemoAccount::user)
                .filter(this::isStoreAccount)
                .filter(user -> user.getStoreId() != null && StringUtils.hasText(user.getStoreName()))
                .collect(Collectors.groupingBy(
                        user -> user.getStoreId() + "@" + user.getStoreName().trim(),
                        Collectors.toList()))
                .values()
                .stream()
                .map(users -> {
                    AuthenticatedUser seed = users.get(0);
                    List<AuthStoreOptionResponse.AccountOption> accounts = users.stream()
                            .sorted(Comparator.comparing(AuthenticatedUser::getDisplayName, Comparator.nullsLast(String::compareTo)))
                            .map(user -> new AuthStoreOptionResponse.AccountOption(
                                    user.getUsername(),
                                    user.getDisplayName(),
                                    user.getRoleCode(),
                                    user.getRoleName(),
                                    user.getStoreName()))
                            .toList();
                    return new AuthStoreOptionResponse(seed.getStoreId(), seed.getStoreName(), accounts);
                })
                .sorted(Comparator
                        .comparing(AuthStoreOptionResponse::getStoreName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(AuthStoreOptionResponse::getStoreId, Comparator.nullsLast(Long::compareTo)))
                .toList();
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

    private boolean isStoreAccount(AuthenticatedUser user) {
        return user != null && STORE_ROLE_CODES.contains(normalize(user.getRoleCode()).toUpperCase(Locale.ROOT));
    }

    private void validateStoreSelection(AuthenticatedUser user, Long selectedStoreId, String selectedStoreName) {
        if (selectedStoreId == null && !StringUtils.hasText(selectedStoreName)) {
            throw new BusinessException("门店登录前请先选择门店");
        }
        boolean storeIdMatched = selectedStoreId != null && selectedStoreId.equals(user.getStoreId());
        boolean storeNameMatched = StringUtils.hasText(selectedStoreName)
                && normalize(selectedStoreName).equalsIgnoreCase(normalize(user.getStoreName()));
        if (!storeIdMatched && !storeNameMatched) {
            throw new BusinessException("账号不属于当前选择的门店，请重新选择");
        }
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
                source.getStoreName(),
                source.getTeamMemberIds() == null ? List.of() : List.copyOf(source.getTeamMemberIds()),
                source.getBoundCustomerUserId(),
                source.getAllowedModules() == null ? List.of() : List.copyOf(source.getAllowedModules()));
    }

    private record DemoAccount(String username, String password, AuthenticatedUser user) {
    }
}
