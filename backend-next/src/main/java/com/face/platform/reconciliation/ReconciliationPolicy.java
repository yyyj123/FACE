package com.face.platform.reconciliation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ReconciliationPolicy {

    private ReconciliationPolicy() {
    }

    public static String resultStatus(
        long systemTransactionCount,
        BigDecimal systemPaymentAmount,
        BigDecimal systemRefundAmount,
        long channelTransactionCount,
        BigDecimal channelPaymentAmount,
        BigDecimal channelRefundAmount
    ) {
        if (systemTransactionCount < 0 || channelTransactionCount < 0) {
            throw new IllegalArgumentException("对账笔数不能小于零");
        }
        boolean matches = systemTransactionCount == channelTransactionCount
            && money(systemPaymentAmount, "系统支付金额").compareTo(
                money(channelPaymentAmount, "通道支付金额")
            ) == 0
            && money(systemRefundAmount, "系统退款金额").compareTo(
                money(channelRefundAmount, "通道退款金额")
            ) == 0;
        return matches ? "MATCHED" : "DIFFERENT";
    }

    private static BigDecimal money(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + "最多两位小数", exception);
        }
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(field + "不能小于零");
        }
        return normalized;
    }
}

