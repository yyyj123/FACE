package com.face.platform.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderVoidRequest(
    @NotNull(message = "请选择消费门店")
    Long shopId,

    @NotNull(message = "缺少订单数据版本")
    Integer version,

    @Size(max = 500, message = "作废原因不能超过500个字符")
    String reason
) {
}
