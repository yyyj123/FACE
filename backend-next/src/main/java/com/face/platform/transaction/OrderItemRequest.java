package com.face.platform.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record OrderItemRequest(
    @NotNull(message = "请选择消费项目类型")
    @Pattern(regexp = "SERVICE|PRODUCT|PACKAGE", message = "不支持的消费项目类型")
    String itemType,

    @NotNull(message = "请选择消费项目")
    Long referenceId,

    @NotNull(message = "请填写数量")
    @DecimalMin(value = "0.001", message = "数量必须大于0")
    @Digits(integer = 9, fraction = 3, message = "数量格式不正确")
    BigDecimal quantity,

    @DecimalMin(value = "0.00", message = "优惠金额不能小于0")
    @Digits(integer = 8, fraction = 2, message = "优惠金额格式不正确")
    BigDecimal discountAmount
) {
}
