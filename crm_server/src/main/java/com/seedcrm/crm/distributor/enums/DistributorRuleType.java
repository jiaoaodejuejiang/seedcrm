package com.seedcrm.crm.distributor.enums;

import com.seedcrm.crm.common.exception.BusinessException;

public enum DistributorRuleType {
    PERCENT,
    FIXED;

    public static DistributorRuleType fromCode(String code) {
        try {
            return DistributorRuleType.valueOf(code);
        } catch (Exception exception) {
            throw new BusinessException("invalid distributor rule type: " + code);
        }
    }
}
