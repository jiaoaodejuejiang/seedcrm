package com.seedcrm.crm.salary.service;

import com.seedcrm.crm.salary.dto.SalaryBalanceResponse;
import com.seedcrm.crm.salary.dto.SalaryStatResponse;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import java.util.List;

public interface SalaryService {

    SalaryStatResponse stat(Long userId);

    SalaryBalanceResponse balance(Long userId);

    List<SalaryDetail> listDetails(Long userId);

    List<SalaryDetail> calculateForPlanOrder(Long planOrderId);

    List<SalaryDetail> recalculateForPlanOrder(Long planOrderId);
}
