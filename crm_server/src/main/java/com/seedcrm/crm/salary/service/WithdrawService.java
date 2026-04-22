package com.seedcrm.crm.salary.service;

import com.seedcrm.crm.salary.dto.WithdrawCreateRequest;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.enums.WithdrawStatus;
import java.math.BigDecimal;

public interface WithdrawService {

    BigDecimal getWithdrawableAmount(Long userId);

    WithdrawRecord createWithdraw(WithdrawCreateRequest request);

    WithdrawRecord approveWithdraw(Long withdrawId, WithdrawStatus targetStatus);
}
