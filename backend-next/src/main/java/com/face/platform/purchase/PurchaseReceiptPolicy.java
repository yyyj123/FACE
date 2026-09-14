package com.face.platform.purchase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class PurchaseReceiptPolicy {

    private PurchaseReceiptPolicy() {
    }

    public static BigDecimal requireReceivable(
        BigDecimal orderedQuantity,
        BigDecimal receivedQuantity,
        BigDecimal requestedQuantity
    ) {
        BigDecimal ordered = quantity(orderedQuantity, "采购数量");
        BigDecimal received = nonNegativeQuantity(receivedQuantity, "已收数量");
        BigDecimal requested = quantity(requestedQuantity, "本次收货数量");
        BigDecimal outstanding = ordered.subtract(received);
        if (requested.compareTo(outstanding) > 0) {
            throw new IllegalArgumentException("本次收货数量超过采购单剩余可收数量");
        }
        return requested;
    }

    public static void requireValidDates(LocalDate producedDate, LocalDate expiryDate) {
        if (producedDate != null && expiryDate != null && expiryDate.isBefore(producedDate)) {
            throw new IllegalArgumentException("有效期不能早于生产日期");
        }
    }

    public static String statusAfterReceipt(boolean everyLineFullyReceived) {
        return everyLineFullyReceived ? "RECEIVED" : "PARTIALLY_RECEIVED";
    }

    private static BigDecimal quantity(BigDecimal value, String name) {
        BigDecimal normalized = normalized(value, name);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(name + "必须大于0");
        }
        return normalized;
    }

    private static BigDecimal nonNegativeQuantity(BigDecimal value, String name) {
        BigDecimal normalized = normalized(value, name);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(name + "不能小于0");
        }
        return normalized;
    }

    private static BigDecimal normalized(BigDecimal value, String name) {
        if (value == null) throw new IllegalArgumentException(name + "不能为空");
        try {
            return value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + "最多保留3位小数");
        }
    }
}
