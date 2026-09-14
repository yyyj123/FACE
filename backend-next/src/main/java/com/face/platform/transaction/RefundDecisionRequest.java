package com.face.platform.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RefundDecisionRequest(
    @NotNull(message = "请选择消费门店")
    Long shopId,

    @NotBlank(message = "请选择审核结果")
    @Pattern(regexp = "APPROVE|REJECT", message = "不支持的审核结果")
    String action,

    @NotNull(message = "缺少退款数据版本")
    Integer version,

    @Size(max = 500, message = "审核说明不能超过500个字符")
    String decisionNote
) {
}
