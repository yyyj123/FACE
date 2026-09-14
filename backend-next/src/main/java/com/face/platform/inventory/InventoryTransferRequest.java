package com.face.platform.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InventoryTransferRequest(
    @NotNull(message = "请选择调出门店")
    Long sourceShopId,

    @NotNull(message = "请选择调出库存地点")
    Long sourceLocationId,

    @NotNull(message = "请选择调入库存地点")
    Long destinationLocationId,

    @NotNull(message = "请选择商品")
    Long productId,

    @NotNull(message = "请填写调拨数量")
    @DecimalMin(value = "0.001", message = "调拨数量必须大于0")
    @Digits(integer = 9, fraction = 3, message = "数量最多保留3位小数")
    BigDecimal quantity,

    @NotNull(message = "缺少库存数据版本")
    Integer version,

    @NotBlank(message = "缺少调拨幂等键")
    @Size(max = 80, message = "幂等键不能超过80个字符")
    String idempotencyKey,

    @Size(max = 500, message = "调拨说明不能超过500个字符")
    String remark
) {
}

