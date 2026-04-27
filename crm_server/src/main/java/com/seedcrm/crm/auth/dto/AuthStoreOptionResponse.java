package com.seedcrm.crm.auth.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthStoreOptionResponse {

    private Long storeId;
    private String storeName;
    private List<AccountOption> accounts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountOption {

        private String username;
        private String title;
        private String roleCode;
        private String roleName;
        private String storeName;
    }
}
