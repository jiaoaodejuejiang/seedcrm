package com.seedcrm.crm.finance.enums;

import com.seedcrm.crm.common.exception.BusinessException;

public enum AccountOwnerType {
    USER,
    DISTRIBUTOR,
    PLATFORM;

    public static AccountOwnerType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("ownerType is required");
        }
        try {
            return AccountOwnerType.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("invalid ownerType: " + code);
        }
    }
}
