package com.seedcrm.crm.salary.enums;

import com.seedcrm.crm.common.exception.BusinessException;

public enum SalaryRuleType {
    PERCENT,
    FIXED;

    public static SalaryRuleType fromCode(String code) {
        try {
            return SalaryRuleType.valueOf(code);
        } catch (Exception exception) {
            throw new BusinessException("invalid salary rule type: " + code);
        }
    }
}
