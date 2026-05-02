package com.seedcrm.crm.order.service;

public record OrderVoucherVerificationResult(String providerCode,
                                             String executionMode,
                                             String idempotencyKey,
                                             String responsePayload,
                                             boolean externalVerified) {

    public static OrderVoucherVerificationResult skipped() {
        return new OrderVoucherVerificationResult(null, "LOCAL", null, null, false);
    }
}
