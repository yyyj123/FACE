package com.face.platform.commission;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CommissionAdjustmentPolicy {

    private CommissionAdjustmentPolicy() {
    }

    public static BigDecimal amount(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("提成调整金额不能为空");
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() == 0) {
            throw new IllegalArgumentException("提成调整金额不能为零");
        }
        return normalized;
    }
}
