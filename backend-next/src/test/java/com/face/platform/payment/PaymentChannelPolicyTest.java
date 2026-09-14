package com.face.platform.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentChannelPolicyTest {

    @Test
    void onlyCashAndBalanceAreLocalLedgerMethods() {
        assertThat(PaymentChannelPolicy.executionMode("CASH")).isEqualTo("LOCAL_LEDGER");
        assertThat(PaymentChannelPolicy.executionMode("BALANCE")).isEqualTo("LOCAL_LEDGER");
        assertThat(PaymentChannelPolicy.executionMode("CARD")).isEqualTo("EXTERNAL_ADAPTER");
        assertThat(PaymentChannelPolicy.executionMode("WECHAT")).isEqualTo("EXTERNAL_ADAPTER");
        assertThat(PaymentChannelPolicy.executionMode("ALIPAY")).isEqualTo("EXTERNAL_ADAPTER");
        assertThat(PaymentChannelPolicy.executionMode("SANDBOX")).isEqualTo("EXTERNAL_ADAPTER");
        assertThat(PaymentChannelPolicy.executionMode("DEMO_MOCK")).isEqualTo("EXTERNAL_ADAPTER");
        assertThat(PaymentChannelPolicy.executionMode("ZERO_AMOUNT")).isEqualTo("ZERO_AMOUNT");
    }

    @Test
    void paymentCallbackCanOnlyLeavePendingOnce() {
        assertThat(PaymentChannelPolicy.callbackTarget("PENDING", "SUCCESS")).isEqualTo("SUCCESS");
        assertThat(PaymentChannelPolicy.callbackTarget("PENDING", "FAILED")).isEqualTo("FAILED");
        assertThat(PaymentChannelPolicy.callbackTarget("SUCCESS", "SUCCESS")).isEqualTo("SUCCESS");
        assertThatThrownBy(() -> PaymentChannelPolicy.callbackTarget("SUCCESS", "FAILED"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
