package com.face.platform.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsVerificationPolicyTest {

    @Test
    void acceptsOnlyMainlandMobileNumbersAndKnownPurposes() {
        assertThat(SmsVerificationPolicy.normalizePhone(" 13800138000 "))
            .isEqualTo("13800138000");
        assertThat(SmsVerificationPolicy.normalizePurpose("password_reset"))
            .isEqualTo("PASSWORD_RESET");

        assertThatThrownBy(() -> SmsVerificationPolicy.normalizePhone("888888"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmsVerificationPolicy.normalizePurpose("PAYMENT"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fixedDemoCodeCannotBeRequestedOutsideDemoProfile() {
        assertThat(SmsVerificationPolicy.demoCode(true)).isEqualTo("888888");
        assertThatThrownBy(() -> SmsVerificationPolicy.demoCode(false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("demo");
    }
}
