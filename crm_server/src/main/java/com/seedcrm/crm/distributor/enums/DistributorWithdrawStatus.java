package com.seedcrm.crm.distributor.enums;

import com.seedcrm.crm.common.exception.BusinessException;

public enum DistributorWithdrawStatus {
    PENDING,
    APPROVED,
    PAID;

    public static DistributorWithdrawStatus fromCode(String code) {
        try {
            return DistributorWithdrawStatus.valueOf(code);
        } catch (Exception exception) {
            throw new BusinessException("invalid distributor withdraw status: " + code);
        }
    }
}
