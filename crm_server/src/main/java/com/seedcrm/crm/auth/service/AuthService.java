package com.seedcrm.crm.auth.service;

import com.seedcrm.crm.auth.model.AuthenticatedUser;
import java.util.Optional;

public interface AuthService {

    String login(String username, String password);

    Optional<AuthenticatedUser> resolve(String token);

    void logout(String token);

    AuthenticatedUser getUserOrThrow(String token);

    Long resolveStoreId(Long userId);
}
