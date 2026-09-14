package com.face.platform.points;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PointsPolicyTest {

    @Test
    void redemptionHonorsRatioAmountStepAndMinimum() {
        var result = PointsPolicy.redemption(
            9_999, new BigDecimal("120"), 100, 100, 100,
            new BigDecimal("50"), new BigDecimal("40")
        );
        assertThat(result.points()).isEqualTo(4_000);
        assertThat(result.discountAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void fefoAllocationUsesEarliestBatchAndNeverOverdraws() {
        var result = PointsPolicy.allocateFefo(120, List.of(
            new PointsPolicy.BatchBalance(1, 100, 20),
            new PointsPolicy.BatchBalance(2, 100, 0)
        ));
        assertThat(result).containsExactly(
            new PointsPolicy.Allocation(1, 80),
            new PointsPolicy.Allocation(2, 40)
        );
    }

    @Test
    void finalCheckinDayIncludesCycleBonus() {
        assertThat(PointsPolicy.checkinReward(List.of(10, 10, 20), 3, 30)).isEqualTo(50);
    }
}
