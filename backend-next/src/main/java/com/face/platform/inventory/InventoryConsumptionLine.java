package com.face.platform.inventory;

import java.math.BigDecimal;

public record InventoryConsumptionLine(
    long locationId,
    long productId,
    BigDecimal quantity,
    int balanceVersion
) {
}

