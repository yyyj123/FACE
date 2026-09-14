package com.face.platform.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateRequest(
    @NotNull(message = "请选择消费门店")
    Long shopId,

    @NotNull(message = "请选择会员")
    Long memberId,

    Long appointmentId,

    @Size(max = 20, message = "单笔订单最多20个消费项目")
    List<@Valid OrderItemRequest> items,

    @Size(max = 500, message = "订单备注不能超过500个字符")
    String notes
) {
}
