package com.face.platform.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundCreateRequest(
    @NotNull(message = "请选择消费门店")
    Long shopId,

    @NotNull(message = "请选择原收款记录")
    Long paymentId,

    @NotNull(message = "请填写退款金额")
    @DecimalMin(value = "0.01", message = "退款金额必须大于0")
    @Digits(integer = 11, fraction = 2, message = "退款金额格式不正确")
    BigDecimal amount,

    @NotBlank(message = "请填写退款原因")
    @Size(max = 500, message = "退款原因不能超过500个字符")
    String reason,

    @NotBlank(message = "缺少退款幂等键")
    @Size(max = 80, message = "退款幂等键不能超过80个字符")
    String idempotencyKey
) {
}
