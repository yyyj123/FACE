package com.face.platform.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

public final class RefundExecutionPolicy {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "PENDING", Set.of("APPROVED", "REJECTED"),
        "APPROVED", Set.of("PROCESSING"),
        "PROCESSING", Set.of("SUCCESS", "FAILED"),
        "FAILED", Set.of("PROCESSING"),
        "SUCCESS", Set.of(),
        "REJECTED", Set.of()
    );

    private RefundExecutionPolicy() {
    }

    public static BigDecimal refundableAmount(
        BigDecimal paymentAmount,
        BigDecimal successfulRefundedAmount,
        BigDecimal reservedRefundAmount
    ) {
        BigDecimal payment = money(paymentAmount, false, "原支付金额");
        BigDecimal successful = money(successfulRefundedAmount, false, "已成功退款金额");
        BigDecimal reserved = money(reservedRefundAmount, false, "已保留退款金额");
        BigDecimal available = payment.subtract(successful).subtract(reserved);
        if (available.signum() < 0) {
            throw new IllegalArgumentException("退款汇总超过原支付金额");
        }
        return available.setScale(2);
    }

    public static boolean canReserve(
        BigDecimal paymentAmount,
        BigDecimal successfulRefundedAmount,
        BigDecimal reservedRefundAmount,
        BigDecimal requestedAmount
    ) {
        BigDecimal requested = money(requestedAmount, true, "申请退款金额");
        return requested.compareTo(
            refundableAmount(paymentAmount, successfulRefundedAmount, reservedRefundAmount)
        ) <= 0;
    }

    public static void requireReservable(
        BigDecimal paymentAmount,
        BigDecimal successfulRefundedAmount,
        BigDecimal reservedRefundAmount,
        BigDecimal requestedAmount
    ) {
        if (!canReserve(paymentAmount, successfulRefundedAmount, reservedRefundAmount, requestedAmount)) {
            throw new IllegalArgumentException("退款金额超过原支付可退余额");
        }
    }

    public static boolean canTransition(String from, String to) {
        return from != null
            && to != null
            && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    private static BigDecimal money(BigDecimal value, boolean positive, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + "最多两位小数", exception);
        }
        if (positive ? normalized.signum() <= 0 : normalized.signum() < 0) {
            throw new IllegalArgumentException(field + (positive ? "必须大于零" : "不能小于零"));
        }
        return normalized;
    }
}

