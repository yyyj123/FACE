package com.face.platform.servicecare;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ServiceCompleteRequest(
    @NotNull(message = "请选择门店")
    Long shopId,

    @NotNull(message = "缺少服务记录数据版本")
    Integer version,

    @NotBlank(message = "请填写本次服务总结")
    @Size(max = 1000, message = "服务总结不能超过1000个字符")
    String serviceSummary,

    @Size(max = 1000, message = "下次到店建议不能超过1000个字符")
    String nextVisitRecommendation,

    @Size(max = 50, message = "肤质不能超过50个字符")
    String skinType,

    @Size(max = 12, message = "皮肤关注点最多选择12项")
    List<@Size(max = 50, message = "关注点不能超过50个字符") String> concerns,

    @NotBlank(message = "请填写护理观察")
    @Size(max = 4000, message = "护理观察不能超过4000个字符")
    String observations,

    @Size(max = 4000, message = "居家护理建议不能超过4000个字符")
    String homeCareAdvice,

    LocalDate nextRecommendedAt,

    @Size(max = 20, message = "单次最多填写20条耗材领用")
    List<@Valid ServiceConsumptionRequest> consumptions,

    @NotBlank(message = "缺少完成服务幂等键")
    @Size(max = 80, message = "幂等键不能超过80个字符")
    String idempotencyKey
) {
}

