package com.face.platform.points;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class PointsPolicy {

    private PointsPolicy() {
    }

    public static Redemption redemption(
        long availablePoints,
        BigDecimal subtotal,
        int pointsPerCurrency,
        int minimumPoints,
        int stepPoints,
        BigDecimal maxDiscountRatio,
        BigDecimal maxDiscountAmount
    ) {
        if (availablePoints < 0 || pointsPerCurrency <= 0 || minimumPoints <= 0 || stepPoints <= 0) {
            throw new IllegalArgumentException("积分抵扣配置无效");
        }
        BigDecimal safeSubtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ratioLimit = safeSubtotal.multiply(maxDiscountRatio)
            .divide(new BigDecimal("100"), 2, RoundingMode.DOWN);
        BigDecimal amountLimit = maxDiscountAmount == null ? ratioLimit : ratioLimit.min(maxDiscountAmount);
        long pointLimit = amountLimit.multiply(BigDecimal.valueOf(pointsPerCurrency)).longValue();
        long usable = Math.min(availablePoints, pointLimit);
        usable = usable - usable % stepPoints;
        if (usable < minimumPoints) return new Redemption(0, BigDecimal.ZERO.setScale(2));
        BigDecimal discount = BigDecimal.valueOf(usable)
            .divide(BigDecimal.valueOf(pointsPerCurrency), 2, RoundingMode.DOWN)
            .min(safeSubtotal);
        return new Redemption(usable, discount);
    }

    public static List<Allocation> allocateFefo(long requested, List<BatchBalance> batches) {
        if (requested <= 0) throw new IllegalArgumentException("冻结积分必须大于 0");
        long remaining = requested;
        List<Allocation> result = new ArrayList<>();
        for (BatchBalance batch : batches) {
            long available = Math.max(0, batch.remainingPoints() - batch.frozenPoints());
            long allocated = Math.min(available, remaining);
            if (allocated > 0) result.add(new Allocation(batch.batchId(), allocated));
            remaining -= allocated;
            if (remaining == 0) break;
        }
        if (remaining > 0) throw new IllegalArgumentException("可用积分不足");
        return List.copyOf(result);
    }

    public static int checkinReward(List<Integer> dailyRewards, int cycleDay, int cycleBonus) {
        if (dailyRewards == null || dailyRewards.isEmpty() || cycleDay < 1 || cycleDay > dailyRewards.size()) {
            throw new IllegalArgumentException("签到阶梯配置无效");
        }
        return dailyRewards.get(cycleDay - 1) + (cycleDay == dailyRewards.size() ? cycleBonus : 0);
    }

    public record Redemption(long points, BigDecimal discountAmount) {
    }

    public record BatchBalance(long batchId, long remainingPoints, long frozenPoints) {
    }

    public record Allocation(long batchId, long points) {
    }
}
