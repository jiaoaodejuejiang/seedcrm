package com.seedcrm.crm.payment.service;

import com.seedcrm.crm.payment.dto.PaymentConfigTestRequest;
import com.seedcrm.crm.payment.dto.PaymentConfigTestResponse;

public interface PaymentConfigService {

    PaymentConfigTestResponse testConfig(PaymentConfigTestRequest request);
}
