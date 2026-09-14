package com.face.platform.servicecare;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CareCorrectionRequest(
    @NotNull(message = "请选择门店")
    Long shopId,

    @NotNull(message = "缺少护理记录版本")
    Integer serviceRecordVersion,

    @NotBlank(message = "请填写更正原因")
    @Size(max = 500, message = "更正原因不能超过500个字符")
    String reason,

    @NotEmpty(message = "请填写至少一个更正字段")
    Map<String, Object> correctedFields,

    @NotBlank(message = "缺少幂等键")
    @Size(max = 80, message = "幂等键不能超过80个字符")
    String idempotencyKey
) {
}
