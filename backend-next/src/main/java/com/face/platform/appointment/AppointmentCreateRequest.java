package com.face.platform.appointment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record AppointmentCreateRequest(
    @NotNull(message = "请选择预约门店")
    Long shopId,

    @NotNull(message = "请选择会员")
    Long memberId,

    @NotNull(message = "请选择美容师")
    Long staffId,

    @NotEmpty(message = "请至少选择一个美容项目")
    @Size(max = 10, message = "一次预约最多选择10个项目")
    List<Long> serviceIds,

    @Size(max = 20, message = "一次预约最多选择20个房间或设备")
    List<Long> resourceIds,

    @NotNull(message = "请选择预约时间")
    LocalDateTime startAt,

    @Pattern(
        regexp = "ONLINE|FRONT_DESK|PHONE|WECHAT",
        message = "不支持的预约来源"
    )
    String source,

    @Size(max = 1000, message = "会员备注不能超过1000个字符")
    String memberNote,

    @Size(max = 1000, message = "内部备注不能超过1000个字符")
    String internalNote
) {
}
