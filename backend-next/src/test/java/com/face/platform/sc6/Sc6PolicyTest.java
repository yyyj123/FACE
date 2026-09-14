package com.face.platform.sc6;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Sc6PolicyTest {

    @Test
    void calculatesOneDecimalAverageAndLowScoreTrigger() {
        assertThat(Sc6Policy.average(5, 4, 4)).isEqualByComparingTo("4.3");
        assertThat(Sc6Policy.triggersContact(5, 2, 5, false)).isTrue();
        assertThat(Sc6Policy.triggersContact(5, 5, 5, true)).isTrue();
        assertThat(Sc6Policy.triggersContact(3, 3, 3, false)).isFalse();
        assertThatThrownBy(() -> Sc6Policy.average(0, 5, 5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesSevenDayWindowAdminLimitAndSingleReopen() {
        LocalDateTime completed = LocalDateTime.of(2026, 8, 1, 10, 0);
        assertThat(Sc6Policy.withinServiceAfterSaleWindow(
            completed, completed.plusDays(7)
        )).isTrue();
        assertThat(Sc6Policy.withinServiceAfterSaleWindow(
            completed, completed.plusDays(7).plusNanos(1)
        )).isFalse();
        assertThat(Sc6Policy.requiresSuperAdmin(new BigDecimal("500.00"))).isFalse();
        assertThat(Sc6Policy.requiresSuperAdmin(new BigDecimal("500.01"))).isTrue();
        assertThat(Sc6Policy.mayReopen(0, completed.plusDays(7), completed.plusDays(1))).isTrue();
        assertThat(Sc6Policy.mayReopen(1, completed.plusDays(7), completed.plusDays(1))).isFalse();
    }
}
