package com.face.platform.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
    @NotNull(message = "请选择消费门店")
    Long shopId,

    @NotBlank(message = "请选择收款方式")
    @Pattern(
        regexp = "CASH|CARD|WECHAT|ALIPAY|BALANCE",
        message = "不支持的收款方式"
    )
    String paymentMethod,

    @NotNull(message = "请填写收款金额")
    @DecimalMin(value = "0.01", message = "收款金额必须大于0")
    @Digits(integer = 11, fraction = 2, message = "收款金额格式不正确")
    BigDecimal amount,

    @NotNull(message = "缺少订单数据版本")
    Integer version,

    @NotBlank(message = "缺少收款幂等键")
    @Size(max = 80, message = "收款幂等键不能超过80个字符")
    String idempotencyKey,

    @Size(max = 100, message = "外部交易号不能超过100个字符")
    String externalTransactionNo
) {
}
