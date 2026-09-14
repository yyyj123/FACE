package com.face.platform.booking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingWaitlistPolicyTest {

    private final BookingWaitlistPolicy policy = new BookingWaitlistPolicy();

    @Test
    void preservesRequiredConfirmationPathWithoutAutoAppointment() {
        assertThat(policy.canTransition("WAITING", "MATCHED")).isTrue();
        assertThat(policy.canTransition("MATCHED", "WAITING_CONFIRMATION")).isTrue();
        assertThat(policy.canTransition("WAITING_CONFIRMATION", "CONFIRMED")).isTrue();
        assertThat(policy.canTransition("WAITING", "CONFIRMED")).isFalse();
        assertThat(policy.createsAppointment("MATCHED")).isFalse();
        assertThat(policy.createsAppointment("WAITING_CONFIRMATION")).isFalse();
        assertThat(policy.createsAppointment("CONFIRMED")).isTrue();
    }

    @Test
    void allowsExplicitTerminalOutcomesOnlyFromActiveStates() {
        assertThat(policy.canTransition("WAITING", "CANCELLED")).isTrue();
        assertThat(policy.canTransition("WAITING_CONFIRMATION", "EXPIRED")).isTrue();
        assertThat(policy.canTransition("MATCHED", "INVALID")).isTrue();
        assertThat(policy.canTransition("CONFIRMED", "WAITING")).isFalse();
        assertThat(policy.canTransition("EXPIRED", "WAITING")).isFalse();
    }
}
