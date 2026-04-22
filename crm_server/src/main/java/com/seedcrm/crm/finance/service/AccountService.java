package com.seedcrm.crm.finance.service;

import com.seedcrm.crm.finance.entity.Account;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import java.math.BigDecimal;

public interface AccountService {

    Long PLATFORM_OWNER_ID = 0L;

    Account getOrCreateAccount(AccountOwnerType ownerType, Long ownerId);

    Account lockAccount(Long accountId);

    BigDecimal getBalance(Long accountId);
}
