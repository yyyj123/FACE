package com.face.platform.commission;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommissionEntryPolicyTest {

    @Test
    void freezeAndUnfreezeFollowExplicitStateTransitions() {
        assertThat(CommissionEntryPolicy.transition("PENDING", "FREEZE"))
            .isEqualTo("FROZEN");
        assertThat(CommissionEntryPolicy.transition("FROZEN", "UNFREEZE"))
            .isEqualTo("PENDING");

        assertThatThrownBy(() -> CommissionEntryPolicy.transition("SETTLED", "FREEZE"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommissionEntryPolicy.transition("PENDING", "UNFREEZE"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partialRefundUsesDeterministicMoneyRounding() {
        assertThat(CommissionEntryPolicy.reversalAmount(
            new BigDecimal("19.80"),
            BigDecimal.ZERO,
            new BigDecimal("50.00"),
            new BigDecimal("168.00")
        )).isEqualByComparingTo("-5.89");
    }

    @Test
    void finalRefundConsumesRemainingAmountWithoutOverReversal() {
        assertThat(CommissionEntryPolicy.reversalAmount(
            new BigDecimal("19.80"),
            new BigDecimal("14.14"),
            new BigDecimal("118.00"),
            new BigDecimal("168.00")
        )).isEqualByComparingTo("-5.66");
    }

    @Test
    void invalidRefundAmountsAreRejected() {
        assertThatThrownBy(() -> CommissionEntryPolicy.reversalAmount(
            new BigDecimal("10.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("100.00")
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommissionEntryPolicy.reversalAmount(
            new BigDecimal("10.00"),
            BigDecimal.ZERO,
            new BigDecimal("101.00"),
            new BigDecimal("100.00")
        )).isInstanceOf(IllegalArgumentException.class);
    }
}

