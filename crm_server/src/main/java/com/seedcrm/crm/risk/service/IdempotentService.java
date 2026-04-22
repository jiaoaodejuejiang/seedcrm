package com.seedcrm.crm.risk.service;

import com.seedcrm.crm.risk.entity.IdempotentRecord;
import com.seedcrm.crm.risk.enums.IdempotentBizType;

public interface IdempotentService {

    boolean tryStart(String bizKey, IdempotentBizType bizType);

    void markSuccess(String bizKey);

    void markFail(String bizKey);

    IdempotentRecord getByBizKey(String bizKey);
}
