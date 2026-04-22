package com.seedcrm.crm.salary.enums;

import com.seedcrm.crm.common.exception.BusinessException;

public enum WithdrawStatus {
    PENDING,
    APPROVED,
    PAID;

    public static WithdrawStatus fromCode(String code) {
        try {
            return WithdrawStatus.valueOf(code);
        } catch (Exception exception) {
            throw new BusinessException("invalid withdraw status: " + code);
        }
    }
}
