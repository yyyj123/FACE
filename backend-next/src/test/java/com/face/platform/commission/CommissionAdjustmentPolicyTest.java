package com.face.platform.commission;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommissionAdjustmentPolicyTest {

    @Test
    void adjustmentMustBeNonZeroAndUsesDeterministicMoneyScale() {
        assertThat(CommissionAdjustmentPolicy.amount(new BigDecimal("12.345")))
            .isEqualByComparingTo("12.35");
        assertThat(CommissionAdjustmentPolicy.amount(new BigDecimal("-4.444")))
            .isEqualByComparingTo("-4.44");
        assertThatThrownBy(() -> CommissionAdjustmentPolicy.amount(BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
