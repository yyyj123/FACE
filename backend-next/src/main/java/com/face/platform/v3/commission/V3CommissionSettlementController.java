package com.face.platform.v3.commission;

import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.settlement.CommissionSettlementApplicationService;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v3/commission")
public class V3CommissionSettlementController {

    private final CommissionSettlementApplicationService settlementService;
    private final CommandIdempotencyService idempotencyService;

    public V3CommissionSettlementController(
        CommissionSettlementApplicationService settlementService,
        CommandIdempotencyService idempotencyService
    ) {
        this.settlementService = settlementService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/settlements")
    public V3ApiResponse<Map<String, Object>> settlements(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return success(
            settlementService.list(principal(request), shopId, status, page, pageSize),
            request
        );
    }

    @PostMapping("/settlements")
    public V3ApiResponse<Map<String, Object>> create(
        @Valid @RequestBody CreateBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            body.shop_id(), body.period_start(), body.period_end(), body.currency_code()
        );
        return idempotent(
            principal, key, "COMMISSION_SETTLEMENT_CREATE", hash,
            () -> settlementService.create(
                principal, body.shop_id(), body.period_start(),
                body.period_end(), body.currency_code(), key, hash
            ),
            () -> settlementService.detailByCreateKey(principal, body.shop_id(), key),
            request
        );
    }

    @GetMapping("/settlements/{batchId}")
    public V3ApiResponse<Map<String, Object>> detail(
        @PathVariable long batchId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(settlementService.detail(principal(request), shopId, batchId), request);
    }

    @PostMapping("/settlements/{batchId}/calculate")
    public V3ApiResponse<Map<String, Object>> calculate(
        @PathVariable long batchId,
        @Valid @RequestBody VersionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(batchId, body.shop_id(), body.version(), "CALCULATE");
        return idempotent(
            principal, key, "COMMISSION_SETTLEMENT_CALCULATE", hash,
            () -> settlementService.calculate(principal, body.shop_id(), batchId, body.version()),
            () -> settlementService.detail(principal, body.shop_id(), batchId),
            request
        );
    }

    @PostMapping("/settlements/{batchId}/confirm")
    public V3ApiResponse<Map<String, Object>> confirm(
        @PathVariable long batchId,
        @Valid @RequestBody VersionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(batchId, body.shop_id(), body.version(), "CONFIRM");
        return idempotent(
            principal, key, "COMMISSION_SETTLEMENT_CONFIRM", hash,
            () -> settlementService.confirm(
                principal, body.shop_id(), batchId, body.version(), key, hash
            ),
            () -> settlementService.detail(principal, body.shop_id(), batchId),
            request
        );
    }

    @PostMapping("/settlements/{batchId}/mark-paid")
    public V3ApiResponse<Map<String, Object>> markPaid(
        @PathVariable long batchId,
        @Valid @RequestBody PaidBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            batchId, body.shop_id(), body.version(), body.payment_reference()
        );
        return idempotent(
            principal, key, "COMMISSION_SETTLEMENT_PAY", hash,
            () -> settlementService.markPaid(
                principal, body.shop_id(), batchId, body.version(), body.payment_reference()
            ),
            () -> settlementService.detail(principal, body.shop_id(), batchId),
            request
        );
    }

    @PostMapping("/settlements/{batchId}/close")
    public V3ApiResponse<Map<String, Object>> close(
        @PathVariable long batchId,
        @Valid @RequestBody VersionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(batchId, body.shop_id(), body.version(), "CLOSE");
        return idempotent(
            principal, key, "COMMISSION_SETTLEMENT_CLOSE", hash,
            () -> settlementService.close(principal, body.shop_id(), batchId, body.version()),
            () -> settlementService.detail(principal, body.shop_id(), batchId),
            request
        );
    }

    @PostMapping("/settlements/{batchId}/void")
    public V3ApiResponse<Map<String, Object>> voidBatch(
        @PathVariable long batchId,
        @Valid @RequestBody VoidBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            batchId, body.shop_id(), body.version(), body.reason(), "VOID"
        );
        return idempotent(
            principal, key, "COMMISSION_SETTLEMENT_VOID", hash,
            () -> settlementService.voidBatch(
                principal, body.shop_id(), batchId, body.version(),
                body.reason(), key, hash
            ),
            () -> settlementService.detail(principal, body.shop_id(), batchId),
            request
        );
    }

    @GetMapping("/technician/commission-summary")
    public V3ApiResponse<Map<String, Object>> technicianSummary(
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(settlementService.technicianSummary(principal(request), shopId), request);
    }

    private V3ApiResponse<Map<String, Object>> idempotent(
        TenantPrincipal principal,
        String key,
        String operation,
        String hash,
        Supplier<Map<String, Object>> work,
        Supplier<Map<String, Object>> replay,
        HttpServletRequest request
    ) {
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(principal, key, operation, hash, () -> result[0] = work.get());
        return success(result[0] == null ? replay.get() : result[0], request);
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        return V3RequestSupport.principal(request);
    }

    private String requiredKey(String value) {
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

    public record CreateBody(
        @NotNull Long shop_id,
        @NotNull LocalDate period_start,
        @NotNull LocalDate period_end,
        @Size(min = 3, max = 3) String currency_code
    ) {
    }

    public record VersionBody(
        @NotNull Long shop_id,
        @NotNull Integer version
    ) {
    }

    public record PaidBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 100) String payment_reference
    ) {
    }

    public record VoidBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 500) String reason
    ) {
    }
}
