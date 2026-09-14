package com.face.platform.commission;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommissionRulePolicyTest {

    @Test
    void publishedRulesAreImmutableAndOnlyRetireForward() {
        assertThat(CommissionRulePolicy.canEdit("DRAFT")).isTrue();
        assertThat(CommissionRulePolicy.canEdit("PUBLISHED")).isFalse();
        assertThat(CommissionRulePolicy.canTransition("DRAFT", "PUBLISHED")).isTrue();
        assertThat(CommissionRulePolicy.canTransition("PUBLISHED", "RETIRED")).isTrue();
        assertThat(CommissionRulePolicy.canTransition("PUBLISHED", "DRAFT")).isFalse();
        assertThat(CommissionRulePolicy.canTransition("RETIRED", "PUBLISHED")).isFalse();
    }

    @Test
    void overlapUsesHalfOpenIntervalsAndRejectsInvalidRanges() {
        Instant july = Instant.parse("2026-07-01T00:00:00Z");
        Instant august = Instant.parse("2026-08-01T00:00:00Z");
        Instant september = Instant.parse("2026-09-01T00:00:00Z");

        assertThat(CommissionRulePolicy.overlaps(july, august, august, september)).isFalse();
        assertThat(CommissionRulePolicy.overlaps(july, september, august, null)).isTrue();
        assertThat(CommissionRulePolicy.overlaps(july, null, september, null)).isTrue();
        assertThatThrownBy(() ->
            CommissionRulePolicy.overlaps(august, july, july, september)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decimalCalculationIsDeterministicWithFloorAndCap() {
        assertThat(CommissionRulePolicy.calculate(
            new BigDecimal("1000.01"),
            new BigDecimal("0.123400"),
            new BigDecimal("2.00"),
            new BigDecimal("0.00"),
            new BigDecimal("200.00")
        )).isEqualByComparingTo("125.40");

        assertThat(CommissionRulePolicy.calculate(
            new BigDecimal("10.00"),
            new BigDecimal("0.010000"),
            BigDecimal.ZERO,
            new BigDecimal("5.00"),
            null
        )).isEqualByComparingTo("5.00");

        assertThat(CommissionRulePolicy.calculate(
            new BigDecimal("1000.00"),
            new BigDecimal("0.500000"),
            BigDecimal.ZERO,
            null,
            new BigDecimal("80.00")
        )).isEqualByComparingTo("80.00");
    }
}
