package com.face.platform.store;

import java.math.BigDecimal;

public final class MallPolicy {

    private MallPolicy() {
    }

    public static Price price(
        String mode,
        boolean cashEnabled,
        boolean pointsEnabled,
        boolean comboEnabled,
        BigDecimal cashPrice,
        Long pointsPrice,
        BigDecimal comboCashPrice,
        Long comboPointsPrice,
        int quantity
    ) {
        if (quantity <= 0) throw new IllegalArgumentException("购买数量必须大于 0");
        return switch (mode) {
            case "CASH" -> {
                if (!cashEnabled || cashPrice == null) throw new IllegalArgumentException("SKU 未开放人民币购买");
                yield new Price(cashPrice.multiply(BigDecimal.valueOf(quantity)), 0);
            }
            case "POINTS" -> {
                if (!pointsEnabled || pointsPrice == null) throw new IllegalArgumentException("SKU 未开放纯积分兑换");
                yield new Price(BigDecimal.ZERO.setScale(2), Math.multiplyExact(pointsPrice, quantity));
            }
            case "COMBINATION" -> {
                if (!comboEnabled || comboCashPrice == null || comboPointsPrice == null) {
                    throw new IllegalArgumentException("SKU 未开放积分加人民币购买");
                }
                yield new Price(
                    comboCashPrice.multiply(BigDecimal.valueOf(quantity)),
                    Math.multiplyExact(comboPointsPrice, quantity)
                );
            }
            default -> throw new IllegalArgumentException("不支持的购买模式");
        };
    }

    public static String splitKey(
        String mode,
        String deliveryMode,
        Long pickupShopId,
        String freightTemplateCode,
        boolean separateShipping,
        long skuId
    ) {
        return mode + "|" + deliveryMode + "|" + (pickupShopId == null ? "-" : pickupShopId)
            + "|" + (freightTemplateCode == null ? "-" : freightTemplateCode)
            + "|" + (separateShipping ? "SKU:" + skuId : "SHARED");
    }

    public static void requireInventory(int available, int requested) {
        if (requested <= 0) throw new IllegalArgumentException("冻结库存必须大于 0");
        if (available < requested) throw new IllegalArgumentException("SKU 可售库存不足");
    }

    public record Price(BigDecimal cashAmount, long pointsAmount) {
    }
}
