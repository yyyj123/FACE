package com.face.platform.appointment;

import com.face.platform.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentStatusPolicyTest {

    @Test
    void exposesOnlyLegalFrontDeskTransitions() {
        assertThat(AppointmentStatusPolicy.allowedNext("PENDING"))
            .containsExactly("CONFIRMED", "CANCELLED");
        assertThat(AppointmentStatusPolicy.allowedNext("CONFIRMED"))
            .containsExactly("CHECKED_IN", "CANCELLED", "NO_SHOW");
        assertThat(AppointmentStatusPolicy.allowedNext("COMPLETED")).isEmpty();
    }

    @Test
    void rejectsSkippingRequiredServiceStates() {
        assertThatThrownBy(
            () -> AppointmentStatusPolicy.requireTransition("CONFIRMED", "COMPLETED")
        )
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不能从 CONFIRMED 变更为 COMPLETED");
    }
}
