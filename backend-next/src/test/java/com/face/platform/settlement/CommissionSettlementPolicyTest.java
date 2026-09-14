package com.face.platform.settlement;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionSettlementPolicyTest {

    @Test
    void allowsOnlyTheDocumentedForwardLifecycleAndControlledVoid() {
        assertThat(CommissionSettlementPolicy.canTransition("DRAFT", "CALCULATED")).isTrue();
        assertThat(CommissionSettlementPolicy.canTransition("CALCULATED", "CONFIRMED")).isTrue();
        assertThat(CommissionSettlementPolicy.canTransition("CONFIRMED", "PAID")).isTrue();
        assertThat(CommissionSettlementPolicy.canTransition("PAID", "CLOSED")).isTrue();
        assertThat(CommissionSettlementPolicy.canTransition("DRAFT", "VOIDED")).isTrue();
        assertThat(CommissionSettlementPolicy.canTransition("CALCULATED", "VOIDED")).isTrue();
        assertThat(CommissionSettlementPolicy.canTransition("CONFIRMED", "VOIDED")).isTrue();
        assertThat(CommissionSettlementPolicy.canTransition("PAID", "VOIDED")).isFalse();
        assertThat(CommissionSettlementPolicy.canTransition("CLOSED", "CONFIRMED")).isFalse();
        assertThat(CommissionSettlementPolicy.canTransition("VOIDED", "DRAFT")).isFalse();
    }

    @Test
    void creatorOrCalculatorCannotApproveTheirOwnBatch() {
        assertThat(CommissionSettlementPolicy.canApprove(12L, 12L, 13L)).isFalse();
        assertThat(CommissionSettlementPolicy.canApprove(13L, 12L, 13L)).isFalse();
        assertThat(CommissionSettlementPolicy.canApprove(14L, 12L, 13L)).isTrue();
    }

    @Test
    void settlementPeriodMustBeOrdered() {
        assertThat(CommissionSettlementPolicy.validPeriod(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)
        )).isTrue();
        assertThat(CommissionSettlementPolicy.validPeriod(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 7, 31)
        )).isFalse();
    }
}
