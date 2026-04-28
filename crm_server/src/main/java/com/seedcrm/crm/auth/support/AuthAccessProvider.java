package com.seedcrm.crm.auth.support;

import com.seedcrm.crm.auth.model.AuthenticatedUser;

public interface AuthAccessProvider {

    AuthenticatedUser enrich(AuthenticatedUser user);
}
