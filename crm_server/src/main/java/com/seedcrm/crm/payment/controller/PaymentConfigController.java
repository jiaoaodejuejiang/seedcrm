package com.seedcrm.crm.payment.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.payment.dto.PaymentConfigTestRequest;
import com.seedcrm.crm.payment.dto.PaymentConfigTestResponse;
import com.seedcrm.crm.payment.service.PaymentConfigService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment/config")
public class PaymentConfigController {

    private final PaymentConfigService paymentConfigService;

    public PaymentConfigController(PaymentConfigService paymentConfigService) {
        this.paymentConfigService = paymentConfigService;
    }

    @PostMapping("/test")
    public ApiResponse<PaymentConfigTestResponse> test(@RequestBody PaymentConfigTestRequest request) {
        return ApiResponse.success(paymentConfigService.testConfig(request));
    }
}
