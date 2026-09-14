package com.face.platform.checkout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DiscountSelectionPolicy {

    private static final Set<String> SELECTIONS =
        Set.of("NONE", "ACTIVITY", "COUPON", "DISCOUNT_CARD", "POINTS");

    private DiscountSelectionPolicy() {
    }

    public static String defaultSelection() {
        return "NONE";
    }

    public static String select(List<String> selectedTypes) {
        if (selectedTypes == null || selectedTypes.isEmpty()) return defaultSelection();
        List<String> normalized = selectedTypes.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .distinct()
            .toList();
        if (normalized.isEmpty()) return defaultSelection();
        if (normalized.size() != 1) {
            throw new IllegalArgumentException("一次订单只能选择一种优惠");
        }
        String selected = normalized.getFirst();
        if (!SELECTIONS.contains(selected)) {
            throw new IllegalArgumentException("不支持的优惠类型");
        }
        return selected;
    }

    public static BigDecimal fixedReduction(BigDecimal subtotal, BigDecimal requestedReduction) {
        BigDecimal safeSubtotal = money(subtotal);
        BigDecimal safeReduction = moneyAllowZero(requestedReduction);
        return safeReduction.min(safeSubtotal).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentageReduction(
        BigDecimal subtotal,
        BigDecimal percentage,
        BigDecimal maximumSavings
    ) {
        BigDecimal safeSubtotal = money(subtotal);
        if (percentage == null || percentage.signum() <= 0
            || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("折扣比例必须在 0 到 100 之间");
        }
        BigDecimal reduction = safeSubtotal.multiply(
            BigDecimal.ONE.subtract(percentage.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP))
        ).setScale(2, RoundingMode.HALF_UP);
        if (maximumSavings != null) reduction = reduction.min(moneyAllowZero(maximumSavings));
        return reduction.min(safeSubtotal);
    }

    public static BigDecimal payable(BigDecimal subtotal, BigDecimal discount) {
        BigDecimal safeSubtotal = money(subtotal);
        BigDecimal safeDiscount = moneyAllowZero(discount).min(safeSubtotal);
        return safeSubtotal.subtract(safeDiscount).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        BigDecimal normalized = moneyAllowZero(value);
        if (normalized.signum() <= 0) throw new IllegalArgumentException("订单金额必须大于零");
        return normalized;
    }

    private static BigDecimal moneyAllowZero(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("金额不能为空");
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.signum() < 0) throw new IllegalArgumentException("金额不能小于零");
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("金额最多两位小数", exception);
        }
    }
}
