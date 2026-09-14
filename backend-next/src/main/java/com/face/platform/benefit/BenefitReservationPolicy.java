package com.face.platform.benefit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

public final class BenefitReservationPolicy {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "RESERVED", Set.of("CONSUMED", "RELEASED"),
        "CONSUMED", Set.of("REVERSAL"),
        "RELEASED", Set.of(),
        "REVERSAL", Set.of()
    );

    private BenefitReservationPolicy() {
    }

    public static String transition(String current, String target) {
        if (current == null || target == null
            || !TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalArgumentException("权益冻结状态不能从 " + current + " 变为 " + target);
        }
        return target;
    }

    public static Allocation allocateStoredValue(
        BigDecimal principalAvailable,
        BigDecimal giftAvailable,
        BigDecimal requested
    ) {
        BigDecimal principal = money(principalAvailable);
        BigDecimal gift = money(giftAvailable);
        BigDecimal total = money(requested);
        if (principal.add(gift).compareTo(total) < 0) {
            throw new IllegalArgumentException("储值卡可用余额不足");
        }
        BigDecimal fromPrincipal = principal.min(total);
        BigDecimal fromGift = total.subtract(fromPrincipal);
        return new Allocation(fromPrincipal, fromGift);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("金额不能为空");
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.signum() < 0) throw new IllegalArgumentException("金额不能小于零");
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("金额最多两位小数", exception);
        }
    }

    public record Allocation(BigDecimal principal, BigDecimal gift) {
        public BigDecimal total() {
            return principal.add(gift).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
