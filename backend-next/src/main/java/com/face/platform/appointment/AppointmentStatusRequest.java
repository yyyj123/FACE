package com.face.platform.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AppointmentStatusRequest(
    @NotNull(message = "请选择预约门店")
    Long shopId,

    @NotBlank(message = "请选择目标状态")
    @Pattern(
        regexp = "PENDING|CONFIRMED|CHECKED_IN|IN_SERVICE|COMPLETED|CANCELLED|NO_SHOW",
        message = "不支持的预约状态"
    )
    String status,

    @NotNull(message = "缺少预约数据版本")
    Integer version,

    @Size(max = 500, message = "原因不能超过500个字符")
    String reason
) {
}

