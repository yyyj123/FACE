package com.face.platform.packageaccount;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MemberAccountPolicy {

    private MemberAccountPolicy() {
    }

    public static BigDecimal balanceAfterDebit(
        String accountType,
        String status,
        BigDecimal balance,
        BigDecimal amount
    ) {
        requireActive(status);
        int scale = scale(accountType);
        BigDecimal normalizedBalance = number(balance, scale, false, "账户余额");
        BigDecimal normalizedAmount = number(amount, scale, true, "扣减金额");
        if (normalizedBalance.compareTo(normalizedAmount) < 0) {
            throw new IllegalArgumentException("账户余额不足");
        }
        return normalizedBalance.subtract(normalizedAmount).setScale(scale);
    }

    public static BigDecimal balanceAfterCredit(
        String accountType,
        String status,
        BigDecimal balance,
        BigDecimal amount
    ) {
        requireActive(status);
        int scale = scale(accountType);
        BigDecimal normalizedBalance = number(balance, scale, false, "账户余额");
        BigDecimal normalizedAmount = number(amount, scale, true, "入账金额");
        return normalizedBalance.add(normalizedAmount).setScale(scale);
    }

    private static void requireActive(String status) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalArgumentException("只有生效中的账户可以入账或扣减");
        }
    }

    private static int scale(String accountType) {
        return switch (accountType) {
            case "BALANCE", "GIFT_BALANCE" -> 2;
            case "POINTS" -> 0;
            default -> throw new IllegalArgumentException("不支持的账户类型");
        };
    }

    private static BigDecimal number(BigDecimal value, int scale, boolean positive, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + "小数位不合法", exception);
        }
        if (positive ? normalized.signum() <= 0 : normalized.signum() < 0) {
            throw new IllegalArgumentException(field + (positive ? "必须大于零" : "不能小于零"));
        }
        return normalized;
    }
}

