package com.face.platform.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefundExecuteRequest(
    @NotNull(message = "请选择消费门店")
    Long shopId,

    @NotNull(message = "缺少退款数据版本")
    Integer version,

    @NotBlank(message = "缺少退款执行幂等键")
    @Size(max = 80, message = "退款执行幂等键不能超过80个字符")
    String idempotencyKey
) {
}
