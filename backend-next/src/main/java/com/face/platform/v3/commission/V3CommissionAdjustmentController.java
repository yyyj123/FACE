package com.face.platform.v3.commission;

import com.face.platform.commission.CommissionAdjustmentApplicationService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/commission")
public class V3CommissionAdjustmentController {

    private final CommissionAdjustmentApplicationService adjustmentService;

    public V3CommissionAdjustmentController(
        CommissionAdjustmentApplicationService adjustmentService
    ) {
        this.adjustmentService = adjustmentService;
    }

    @PostMapping("/entries/{entryId}/adjustments")
    public V3ApiResponse<Map<String, Object>> requestAdjustment(
        @PathVariable long entryId,
        @Valid @RequestBody AdjustmentBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            entryId, body.shop_id(), body.amount(), body.reason()
        );
        return V3ApiResponse.success(
            adjustmentService.request(
                V3RequestSupport.principal(request), body.shop_id(), entryId,
                body.amount(), body.reason(), key, hash
            ),
            V3RequestSupport.requestId(request)
        );
    }

    private String requiredKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 80) {
            throw new V3ApiException(
                HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                "关键写操作必须提供有效的 Idempotency-Key"
            );
        }
        return value.trim();
    }

    public record AdjustmentBody(
        @NotNull Long shop_id,
        @NotNull @Digits(integer = 11, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 500) String reason
    ) {
    }
}
