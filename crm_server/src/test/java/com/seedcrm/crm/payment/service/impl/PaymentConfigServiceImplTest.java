package com.seedcrm.crm.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.seedcrm.crm.payment.dto.PaymentConfigTestRequest;
import org.junit.jupiter.api.Test;

class PaymentConfigServiceImplTest {

    private final PaymentConfigServiceImpl service = new PaymentConfigServiceImpl();

    @Test
    void shouldReturnInvalidWhenRequiredFieldsAreMissing() {
        PaymentConfigTestRequest request = new PaymentConfigTestRequest();
        request.setChannel("WECHAT_PAY");
        request.setEnabled(1);
        request.setApiBaseUrl("https://api.seedcrm.test");
        request.setNotifyPath("/pay/wechat/notify");

        var response = service.testConfig(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getStatus()).isEqualTo("INVALID");
        assertThat(response.getMessage()).contains("商户主体");
    }

    @Test
    void shouldReturnSuccessWhenDraftLooksValid() {
        PaymentConfigTestRequest request = new PaymentConfigTestRequest();
        request.setChannel("WECHAT_PAY");
        request.setEnabled(1);
        request.setMerchantName("SeedCRM 商户");
        request.setMchId("1900000109");
        request.setAppId("wx1234567890");
        request.setApiV3Key("abcdefghijklmnopqrstuvwxyz123456");
        request.setSerialNo("SERIAL123");
        request.setPrivateKeyPem("-----BEGIN PRIVATE KEY-----demo");
        request.setApiBaseUrl("https://api.seedcrm.test");
        request.setNotifyPath("/pay/wechat/notify");
        request.setRefundNotifyPath("/pay/wechat/refund-notify");

        var response = service.testConfig(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getNotifyUrl()).isEqualTo("https://api.seedcrm.test/pay/wechat/notify");
        assertThat(response.getRefundNotifyUrl()).isEqualTo("https://api.seedcrm.test/pay/wechat/refund-notify");
    }
}
