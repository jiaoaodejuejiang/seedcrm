package com.seedcrm.crm.risk.service.impl;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.risk.service.RiskControlService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class RiskControlServiceImpl implements RiskControlService {

    @Override
    public void validateSplitTotalNotExceedOrderAmount(BigDecimal orderAmount, BigDecimal... splitAmounts) {
        BigDecimal normalizedOrderAmount = normalizePositive(orderAmount, "order amount");
        BigDecimal totalSplitAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (splitAmounts != null) {
            for (BigDecimal splitAmount : splitAmounts) {
                if (splitAmount == null) {
                    continue;
                }
                if (splitAmount.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("split amount must be non-negative");
                }
                totalSplitAmount = totalSplitAmount.add(splitAmount.setScale(2, RoundingMode.HALF_UP));
            }
        }
        if (totalSplitAmount.compareTo(normalizedOrderAmount) > 0) {
            throw new BusinessException("split total amount exceeds order amount");
        }
    }

    @Override
    public void validateWithdrawAmountNotExceedBalance(BigDecimal withdrawAmount, BigDecimal balance) {
        BigDecimal normalizedWithdrawAmount = normalizePositive(withdrawAmount, "withdraw amount");
        BigDecimal normalizedBalance = normalizeNonNegative(balance, "balance");
        if (normalizedWithdrawAmount.compareTo(normalizedBalance) > 0) {
            throw new BusinessException("withdraw amount exceeds account balance");
        }
    }

    private BigDecimal normalizePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(fieldName + " must be greater than 0");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeNonNegative(BigDecimal value, String fieldName) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(fieldName + " must not be negative");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
