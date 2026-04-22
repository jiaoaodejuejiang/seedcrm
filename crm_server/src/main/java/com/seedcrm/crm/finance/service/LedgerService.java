package com.seedcrm.crm.finance.service;

import com.seedcrm.crm.finance.entity.Ledger;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.enums.LedgerBizType;
import com.seedcrm.crm.finance.enums.LedgerDirection;
import java.math.BigDecimal;

public interface LedgerService {

    Ledger record(AccountOwnerType ownerType,
                  Long ownerId,
                  BigDecimal amount,
                  LedgerBizType bizType,
                  Long bizId,
                  LedgerDirection direction);

    BigDecimal getBalance(Long accountId);

    BigDecimal getBizAmount(Long accountId, LedgerBizType bizType, Long bizId);

    boolean hasRecord(LedgerBizType bizType, Long bizId);
}
