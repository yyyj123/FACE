package com.face.platform.legacyimport;

import jakarta.validation.constraints.NotNull;

public record LegacyImportExecuteRequest(
    @NotNull(message = "请选择门店") Long shopId
) {
}
