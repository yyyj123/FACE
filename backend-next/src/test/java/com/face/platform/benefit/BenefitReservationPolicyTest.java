package com.face.platform.benefit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenefitReservationPolicyTest {

    @Test
    void reservationsOnlyMoveThroughAuditableTerminalStates() {
        assertThat(BenefitReservationPolicy.transition("RESERVED", "CONSUMED"))
            .isEqualTo("CONSUMED");
        assertThat(BenefitReservationPolicy.transition("RESERVED", "RELEASED"))
            .isEqualTo("RELEASED");
        assertThat(BenefitReservationPolicy.transition("CONSUMED", "REVERSAL"))
            .isEqualTo("REVERSAL");
        assertThatThrownBy(() -> BenefitReservationPolicy.transition("RELEASED", "CONSUMED"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storedValueReservationUsesPrincipalBeforeGiftAndPreservesTheTotal() {
        BenefitReservationPolicy.Allocation allocation = BenefitReservationPolicy.allocateStoredValue(
            new BigDecimal("80.00"), new BigDecimal("50.00"), new BigDecimal("100.00")
        );
        assertThat(allocation.principal()).isEqualByComparingTo("80.00");
        assertThat(allocation.gift()).isEqualByComparingTo("20.00");
        assertThat(allocation.total()).isEqualByComparingTo("100.00");
        assertThatThrownBy(() -> BenefitReservationPolicy.allocateStoredValue(
            new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.01")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("余额不足");
    }
}
