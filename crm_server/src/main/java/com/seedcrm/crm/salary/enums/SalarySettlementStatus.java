package com.seedcrm.crm.salary.enums;

import com.seedcrm.crm.common.exception.BusinessException;

public enum SalarySettlementStatus {
    INIT,
    CONFIRMED,
    PAID;

    public static SalarySettlementStatus fromCode(String code) {
        try {
            return SalarySettlementStatus.valueOf(code);
        } catch (Exception exception) {
            throw new BusinessException("invalid settlement status: " + code);
        }
    }
}
