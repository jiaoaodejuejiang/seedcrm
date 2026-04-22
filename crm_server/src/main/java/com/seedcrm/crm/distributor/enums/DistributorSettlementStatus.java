package com.seedcrm.crm.distributor.enums;

import com.seedcrm.crm.common.exception.BusinessException;

public enum DistributorSettlementStatus {
    INIT,
    CONFIRMED,
    PAID;

    public static DistributorSettlementStatus fromCode(String code) {
        try {
            return DistributorSettlementStatus.valueOf(code);
        } catch (Exception exception) {
            throw new BusinessException("invalid distributor settlement status: " + code);
        }
    }
}
