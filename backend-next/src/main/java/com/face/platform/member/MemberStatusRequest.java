package com.face.platform.member;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MemberStatusRequest(
    @NotNull(message = "请选择操作门店")
    Long shopId,

    @Pattern(regexp = "ACTIVE|INACTIVE", message = "不支持的会员状态")
    String status
) {
}

