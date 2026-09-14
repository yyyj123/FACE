package com.face.platform.checkout;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountSelectionPolicyTest {

    @Test
    void defaultsToNoDiscountAndNeverAutoSelectsTheBestCandidate() {
        assertThat(DiscountSelectionPolicy.defaultSelection()).isEqualTo("NONE");
        assertThat(DiscountSelectionPolicy.select(List.of())).isEqualTo("NONE");
        assertThat(DiscountSelectionPolicy.select(List.of("COUPON"))).isEqualTo("COUPON");
    }

    @Test
    void activityCouponDiscountCardAndPointsAreMutuallyExclusive() {
        assertThatThrownBy(() -> DiscountSelectionPolicy.select(List.of("COUPON", "DISCOUNT_CARD")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("只能选择一种优惠");
        assertThat(DiscountSelectionPolicy.select(List.of("POINTS"))).isEqualTo("POINTS");
        assertThatThrownBy(() -> DiscountSelectionPolicy.select(List.of("POINTS", "ACTIVITY")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("只能选择一种优惠");
    }

    @Test
    void moneyRulesRoundOnceAndNeverCreateANegativePayableAmount() {
        assertThat(DiscountSelectionPolicy.fixedReduction(
            new BigDecimal("100.00"), new BigDecimal("120.00")
        )).isEqualByComparingTo("100.00");
        assertThat(DiscountSelectionPolicy.percentageReduction(
            new BigDecimal("199.99"), new BigDecimal("85"), new BigDecimal("20.00")
        )).isEqualByComparingTo("20.00");
        assertThat(DiscountSelectionPolicy.payable(
            new BigDecimal("199.99"), new BigDecimal("20.00")
        )).isEqualByComparingTo("179.99");
    }
}
