package com.face.platform.commission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class CommissionEntryPolicy {

    private CommissionEntryPolicy() {
    }

    public static String transition(String currentStatus, String action) {
        String current = normalize(currentStatus);
        String command = normalize(action);
        if ("PENDING".equals(current) && "FREEZE".equals(command)) {
            return "FROZEN";
        }
        if ("FROZEN".equals(current) && "UNFREEZE".equals(command)) {
            return "PENDING";
        }
        throw new IllegalArgumentException("提成流水当前状态不允许执行该操作");
    }

    public static BigDecimal reversalAmount(
        BigDecimal originalAmount,
        BigDecimal alreadyReversed,
        BigDecimal refundAmount,
        BigDecimal originalPaidAmount
    ) {
        BigDecimal original = positive(originalAmount, "原提成金额");
        BigDecimal reversed = nonNegative(alreadyReversed, "已冲正金额");
        BigDecimal refund = positive(refundAmount, "退款金额");
        BigDecimal paid = positive(originalPaidAmount, "原订单实付金额");
        if (refund.compareTo(paid) > 0) {
            throw new IllegalArgumentException("退款金额不能超过原订单实付金额");
        }
        BigDecimal remaining = original.subtract(reversed).max(BigDecimal.ZERO);
        if (remaining.signum() == 0) {
            throw new IllegalArgumentException("原提成已全部冲正");
        }
        BigDecimal proportional = original
            .multiply(refund)
            .divide(paid, 2, RoundingMode.HALF_UP);
        BigDecimal amount = proportional.min(remaining).setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("本次退款对应的提成冲正金额为零");
        }
        return amount.negate();
    }

    private static BigDecimal positive(BigDecimal value, String label) {
        BigDecimal normalized = nonNegative(value, label);
        if (normalized.signum() == 0) {
            throw new IllegalArgumentException(label + "必须大于零");
        }
        return normalized;
    }

    private static BigDecimal nonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(label + "格式不正确");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
