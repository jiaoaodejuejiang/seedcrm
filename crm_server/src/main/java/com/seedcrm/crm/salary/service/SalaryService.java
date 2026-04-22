package com.seedcrm.crm.salary.service;

import com.seedcrm.crm.salary.dto.SalaryStatResponse;

public interface SalaryService {

    SalaryStatResponse stat(Long userId);
}
