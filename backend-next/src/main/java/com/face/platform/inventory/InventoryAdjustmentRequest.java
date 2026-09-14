package com.face.platform.inventory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InventoryAdjustmentRequest(
    @NotNull(message = "请选择门店")
    Long shopId,

    @NotNull(message = "请选择库存地点")
    Long locationId,

    @NotNull(message = "请选择商品")
    Long productId,

    @NotNull(message = "请填写变动数量")
    @DecimalMin(value = "-999999.999", message = "变动数量超出范围")
    @DecimalMax(value = "999999.999", message = "变动数量超出范围")
    @Digits(integer = 9, fraction = 3, message = "数量最多保留3位小数")
    BigDecimal quantityDelta,

    @NotBlank(message = "请选择业务类型")
    @Pattern(
        regexp = "PURCHASE_IN|RETURN_IN|MANUAL_IN|MANUAL_OUT|SERVICE_USE|ADJUSTMENT",
        message = "不支持的库存业务类型"
    )
    String movementType,

    @NotNull(message = "缺少库存数据版本")
    Integer version,

    @NotBlank(message = "缺少库存操作幂等键")
    @Size(max = 80, message = "幂等键不能超过80个字符")
    String idempotencyKey,

    @Size(max = 80, message = "业务单号不能超过80个字符")
    String referenceNo,

    @NotBlank(message = "请填写库存变动原因")
    @Size(max = 500, message = "库存变动原因不能超过500个字符")
    String remark
) {
}

