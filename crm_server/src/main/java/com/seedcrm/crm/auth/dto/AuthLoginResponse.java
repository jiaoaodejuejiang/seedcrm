package com.seedcrm.crm.auth.dto;

import com.seedcrm.crm.auth.model.AuthenticatedUser;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthLoginResponse {

    private String token;
    private AuthenticatedUser user;
}
