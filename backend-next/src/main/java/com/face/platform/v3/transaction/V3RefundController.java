package com.face.platform.v3.transaction;

import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.transaction.RefundApplicationService;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3")
public class V3RefundController {

    private final RefundApplicationService refundService;
    private final CommandIdempotencyService idempotencyService;

    public V3RefundController(
        RefundApplicationService refundService,
        CommandIdempotencyService idempotencyService
    ) {
        this.refundService = refundService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/payments/{paymentId}")
    public V3ApiResponse<Map<String, Object>> payment(
        @PathVariable long paymentId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            refundService.paymentDetail(principal(request), shopId, paymentId),
            request
        );
    }

    @PostMapping("/payments/{paymentId}/refunds")
    public V3ApiResponse<Map<String, Object>> requestRefund(
        @PathVariable long paymentId,
        @Valid @RequestBody RefundRequestBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        String requestHash = RequestHash.of(
            paymentId,
            body.shop_id(),
            body.order_id(),
            body.amount(),
            body.reason()
        );
        return success(
            refundService.request(
                principal(request),
                body.shop_id(),
                body.order_id(),
                paymentId,
                body.amount(),
                body.reason(),
                key,
                requestHash
            ),
            request
        );
    }

    @PostMapping("/refunds/{refundId}/decision")
    public V3ApiResponse<Map<String, Object>> decideRefund(
        @PathVariable long refundId,
        @Valid @RequestBody RefundDecisionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            requiredIdempotencyKey(idempotencyKey),
            "REFUND_DECISION",
            RequestHash.of(
                refundId,
                body.shop_id(),
                body.version(),
                body.action(),
                body.decision_note()
            ),
            () -> result[0] = refundService.decide(
                principal,
                body.shop_id(),
                refundId,
                body.version(),
                body.action(),
                body.decision_note()
            )
        );
        return success(
            result[0] == null
                ? refundService.detail(principal, body.shop_id(), refundId)
                : result[0],
            request
        );
    }

    @PostMapping("/refunds/{refundId}/execute")
    public V3ApiResponse<Map<String, Object>> executeRefund(
        @PathVariable long refundId,
        @Valid @RequestBody RefundExecuteBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        return success(
            refundService.execute(
                principal(request),
                body.shop_id(),
                refundId,
                body.version(),
                key,
                RequestHash.of(refundId, body.shop_id(), body.version())
            ),
            request
        );
    }

    @GetMapping("/refunds/{refundId}")
    public V3ApiResponse<Map<String, Object>> refund(
        @PathVariable long refundId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(refundService.detail(principal(request), shopId, refundId), request);
    }

    @GetMapping("/members/{memberId}/refunds")
    public V3ApiResponse<List<Map<String, Object>>> memberRefunds(
        @PathVariable long memberId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            refundService.memberRefunds(principal(request), shopId, memberId),
            request
        );
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        return V3RequestSupport.principal(request);
    }

    private String requiredIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 80) {
            throw new V3ApiException(
                HttpStatus.BAD_REQUEST,
                "IDEMPOTENCY_KEY_REQUIRED",
                "关键写操作必须提供有效的 Idempotency-Key"
            );
        }
        return value.trim();
    }

    private <T> V3ApiResponse<T> success(T data, HttpServletRequest request) {
        return V3ApiResponse.success(data, V3RequestSupport.requestId(request));
    }

    public record RefundRequestBody(
        @NotNull Long shop_id,
        @NotNull Long order_id,
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 11, fraction = 2)
        BigDecimal amount,
        @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record RefundDecisionBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank String action,
        @Size(max = 500) String decision_note
    ) {
    }

    public record RefundExecuteBody(
        @NotNull Long shop_id,
        @NotNull Integer version
    ) {
    }
}
