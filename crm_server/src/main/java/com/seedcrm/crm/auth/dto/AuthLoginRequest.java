package com.seedcrm.crm.auth.dto;

import lombok.Data;

@Data
public class AuthLoginRequest {

    private String username;
    private String password;
    private String loginMode;
    private Long storeId;
    private String storeName;
}
