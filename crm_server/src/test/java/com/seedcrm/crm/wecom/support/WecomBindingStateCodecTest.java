package com.seedcrm.crm.wecom.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WecomBindingStateCodecTest {

    @Test
    void encodeShouldUseWecomSafeStateAndDecodeBindingContext() {
        String state = WecomBindingStateCodec.encode(23L, 5101L, 15L, "13800005101");

        assertThat(state).matches("[A-Za-z0-9]+");
        assertThat(state.length()).isLessThanOrEqualTo(128);

        WecomBindingStateCodec.BindingState decoded = WecomBindingStateCodec.decode(state);
        assertThat(decoded.orderId()).isEqualTo(23L);
        assertThat(decoded.userId()).isEqualTo(5101L);
        assertThat(decoded.customerId()).isEqualTo(15L);
        assertThat(decoded.userPhoneHash()).isNotBlank();
    }
}
