package com.seedcrm.crm.risk.service;

import java.math.BigDecimal;

public interface RiskControlService {

    void validateSplitTotalNotExceedOrderAmount(BigDecimal orderAmount, BigDecimal... splitAmounts);

    void validateWithdrawAmountNotExceedBalance(BigDecimal withdrawAmount, BigDecimal balance);
}
