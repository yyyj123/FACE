package com.face.platform.v3.reconciliation;

import com.face.platform.idempotency.RequestHash;
import com.face.platform.reconciliation.ReconciliationApplicationService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/reconciliation-batches")
public class V3ReconciliationController {

    private final ReconciliationApplicationService service;

    public V3ReconciliationController(ReconciliationApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public V3ApiResponse<List<Map<String, Object>>> list(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(defaultValue = "ALL") String status,
        HttpServletRequest request
    ) {
        return success(service.list(principal(request), shopId, status), request);
    }

    @GetMapping("/{batchId}")
    public V3ApiResponse<Map<String, Object>> detail(
        @PathVariable long batchId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(service.detail(principal(request), shopId, batchId), request);
    }

    @PostMapping
    public V3ApiResponse<Map<String, Object>> run(
        @Valid @RequestBody RunBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        return success(
            service.run(
                principal(request),
                body.shop_id(),
                body.channel_code(),
                body.accounting_date(),
                body.channel_payment_count(),
                body.channel_payment_amount(),
                body.channel_refund_count(),
                body.channel_refund_amount(),
                key,
                RequestHash.of(key, body)
            ),
            request
        );
    }

    @PostMapping("/{batchId}/resolve")
    public V3ApiResponse<Map<String, Object>> resolve(
        @PathVariable long batchId,
        @Valid @RequestBody ResolveBody body,
        HttpServletRequest request
    ) {
        return success(
            service.resolve(
                principal(request),
                body.shop_id(),
                batchId,
                body.version(),
                body.resolution_note(),
                body.evidence_reference()
            ),
            request
        );
    }

    @PostMapping("/{batchId}/close")
    public V3ApiResponse<Map<String, Object>> close(
        @PathVariable long batchId,
        @Valid @RequestBody CloseBody body,
        HttpServletRequest request
    ) {
        return success(
            service.close(principal(request), body.shop_id(), batchId, body.version()),
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

    public record RunBody(
        @NotNull Long shop_id,
        @NotBlank String channel_code,
        @NotNull LocalDate accounting_date,
        @Min(0) long channel_payment_count,
        @NotNull @DecimalMin("0.00") @Digits(integer = 11, fraction = 2)
        BigDecimal channel_payment_amount,
        @Min(0) long channel_refund_count,
        @NotNull @DecimalMin("0.00") @Digits(integer = 11, fraction = 2)
        BigDecimal channel_refund_amount
    ) {
    }

    public record ResolveBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 1000) String resolution_note,
        @Size(max = 500) String evidence_reference
    ) {
    }

    public record CloseBody(
        @NotNull Long shop_id,
        @NotNull Integer version
    ) {
    }
}
