package com.face.platform.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MemberCreateRequest(
    @NotNull(message = "请选择归属门店")
    Long shopId,

    @NotBlank(message = "请填写会员姓名")
    @Size(max = 50, message = "会员姓名不能超过50个字符")
    String name,

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^[0-9+\\-\\s]{6,30}$", message = "手机号格式不正确")
    String phone,

    @Size(max = 20, message = "性别不能超过20个字符")
    String gender,

    LocalDate birthday,

    @Size(max = 40, message = "来源不能超过40个字符")
    String source,

    @Size(max = 1000, message = "备注不能超过1000个字符")
    String notes
) {
}

