package com.face.platform.commission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

public final class CommissionRulePolicy {

    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "RETIRED");

    private CommissionRulePolicy() {
    }

    public static boolean canEdit(String status) {
        return "DRAFT".equals(normalizeStatus(status));
    }

    public static boolean canTransition(String current, String target) {
        String from = normalizeStatus(current);
        String to = normalizeStatus(target);
        return ("DRAFT".equals(from) && "PUBLISHED".equals(to))
            || ("PUBLISHED".equals(from) && "RETIRED".equals(to));
    }

    public static boolean overlaps(
        Instant firstStart,
        Instant firstEnd,
        Instant secondStart,
        Instant secondEnd
    ) {
        requireRange(firstStart, firstEnd);
        requireRange(secondStart, secondEnd);
        boolean firstStartsBeforeSecondEnds =
            secondEnd == null || firstStart.isBefore(secondEnd);
        boolean secondStartsBeforeFirstEnds =
            firstEnd == null || secondStart.isBefore(firstEnd);
        return firstStartsBeforeSecondEnds && secondStartsBeforeFirstEnds;
    }

    public static BigDecimal calculate(
        BigDecimal baseAmount,
        BigDecimal rateValue,
        BigDecimal fixedAmount,
        BigDecimal floorAmount,
        BigDecimal capAmount
    ) {
        BigDecimal base = nonNegative(baseAmount, "提成基数");
        BigDecimal rate = nonNegative(rateValue, "提成比例");
        BigDecimal fixed = nonNegative(fixedAmount, "固定提成");
        BigDecimal result = base.multiply(rate).add(fixed);
        if (floorAmount != null) {
            BigDecimal floor = nonNegative(floorAmount, "最低提成");
            if (result.compareTo(floor) < 0) result = floor;
        }
        if (capAmount != null) {
            BigDecimal cap = nonNegative(capAmount, "最高提成");
            if (floorAmount != null && cap.compareTo(floorAmount) < 0) {
                throw new IllegalArgumentException("最高提成不能低于最低提成");
            }
            if (result.compareTo(cap) > 0) result = cap;
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    public static String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的提成规则状态");
        }
        return normalized;
    }

    private static void requireRange(Instant start, Instant end) {
        if (start == null) throw new IllegalArgumentException("生效开始时间不能为空");
        if (end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("生效结束时间必须晚于开始时间");
        }
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + "不能为负数或空值");
        }
        return value;
    }
}
