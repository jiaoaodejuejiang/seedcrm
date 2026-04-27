package com.seedcrm.crm.auth.service;

import com.seedcrm.crm.auth.dto.AuthStoreOptionResponse;
import com.seedcrm.crm.auth.model.AuthenticatedUser;
import java.util.List;
import java.util.Optional;

public interface AuthService {

    default String login(String username, String password) {
        return login(username, password, null, null);
    }

    String login(String username, String password, Long storeId, String storeName);

    List<AuthStoreOptionResponse> listStoreOptions();

    Optional<AuthenticatedUser> resolve(String token);

    void logout(String token);

    AuthenticatedUser getUserOrThrow(String token);

    Long resolveStoreId(Long userId);
}
