package com.face.platform.appointment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record AppointmentRescheduleRequest(
    @NotNull(message = "请选择预约门店")
    Long shopId,

    @NotNull(message = "请选择美容师")
    Long staffId,

    @NotNull(message = "请选择新的预约时间")
    LocalDateTime startAt,

    @Size(max = 20, message = "一次预约最多选择20个房间或设备")
    List<Long> resourceIds,

    @NotNull(message = "缺少预约数据版本")
    Integer version,

    @Size(max = 500, message = "改期原因不能超过500个字符")
    String reason
) {
}
