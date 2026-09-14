package com.face.platform.packageaccount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class PackageLifecyclePolicy {

    private static final int QUANTITY_SCALE = 4;

    private PackageLifecyclePolicy() {
    }

    public static BigDecimal balanceAfterWriteOff(
        String status,
        BigDecimal remaining,
        BigDecimal quantity,
        LocalDate validFrom,
        LocalDate validUntil,
        LocalDate businessDate
    ) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalArgumentException("只有生效中的套餐可以核销");
        }
        if (businessDate == null) {
            throw new IllegalArgumentException("业务日期不能为空");
        }
        if (validFrom != null && businessDate.isBefore(validFrom)) {
            throw new IllegalArgumentException("套餐尚未生效");
        }
        if (validUntil != null && businessDate.isAfter(validUntil)) {
            throw new IllegalArgumentException("套餐已过期");
        }
        BigDecimal normalizedRemaining = quantity(remaining, false, "套餐余额");
        BigDecimal normalizedQuantity = quantity(quantity, true, "核销数量");
        if (normalizedRemaining.compareTo(normalizedQuantity) < 0) {
            throw new IllegalArgumentException("套餐余额不足");
        }
        return normalizedRemaining.subtract(normalizedQuantity).setScale(QUANTITY_SCALE);
    }

    public static String statusAfterBalance(BigDecimal remaining) {
        return quantity(remaining, false, "套餐余额").signum() == 0 ? "EXHAUSTED" : "ACTIVE";
    }

    private static BigDecimal quantity(BigDecimal value, boolean positive, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + "最多四位小数", exception);
        }
        if (positive ? normalized.signum() <= 0 : normalized.signum() < 0) {
            throw new IllegalArgumentException(field + (positive ? "必须大于零" : "不能小于零"));
        }
        return normalized;
    }
}

