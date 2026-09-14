package com.face.platform.servicecare;

import jakarta.validation.constraints.NotNull;

public record ServiceStartRequest(
    @NotNull(message = "请选择门店")
    Long shopId,

    @NotNull(message = "请选择已到店预约")
    Long appointmentId,

    @NotNull(message = "缺少预约数据版本")
    Integer appointmentVersion
) {
}

