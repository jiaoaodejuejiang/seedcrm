package com.seedcrm.crm.order.service;

import com.seedcrm.crm.order.entity.Order;

public interface OrderVoucherVerificationGateway {

    OrderVoucherVerificationResult verify(Order order, String verificationCode, String verificationMethod);
}
