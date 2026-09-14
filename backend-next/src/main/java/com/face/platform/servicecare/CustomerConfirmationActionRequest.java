package com.face.platform.servicecare;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerConfirmationActionRequest(
    @NotBlank(message = "请选择确认结果")
    String action,

    @Size(max = 500, message = "拒绝原因不能超过500个字符")
    String reason,

    @NotNull(message = "缺少确认记录版本")
    Integer version,

    @NotBlank(message = "缺少幂等键")
    @Size(max = 80, message = "幂等键不能超过80个字符")
    String idempotencyKey
) {
}
