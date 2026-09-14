package com.face.platform.servicecare;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ServiceConsumptionRequest(
    @NotNull(message = "请选择库存地点")
    Long locationId,

    @NotNull(message = "请选择耗材")
    Long productId,

    @NotNull(message = "请填写耗材数量")
    @DecimalMin(value = "0.001", message = "耗材数量必须大于0")
    @Digits(integer = 9, fraction = 3, message = "耗材数量最多保留3位小数")
    BigDecimal quantity,

    @NotNull(message = "缺少库存数据版本")
    Integer balanceVersion
) {
}

