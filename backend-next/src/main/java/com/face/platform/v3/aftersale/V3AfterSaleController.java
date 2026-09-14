package com.face.platform.v3.aftersale;

import com.face.platform.aftersale.AfterSaleApplicationService;
import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.security.TenantPrincipal;
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
import java.util.Map;

@RestController
@RequestMapping("/api/v3/after-sales")
public class V3AfterSaleController {

    private final AfterSaleApplicationService afterSaleService;
    private final CommandIdempotencyService idempotencyService;

    public V3AfterSaleController(
        AfterSaleApplicationService afterSaleService,
        CommandIdempotencyService idempotencyService
    ) {
        this.afterSaleService = afterSaleService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/cases")
    public V3ApiResponse<Map<String, Object>> cases(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return success(
            afterSaleService.list(principal(request), shopId, status, page, pageSize),
            request
        );
    }

    @PostMapping("/cases")
    public V3ApiResponse<Map<String, Object>> create(
        @Valid @RequestBody CreateBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            body.shop_id(), body.member_id(), body.order_id(), body.service_record_id(),
            body.category(), body.priority(), body.summary()
        );
        return success(
            afterSaleService.create(
                principal(request), body.shop_id(), body.member_id(), body.order_id(),
                body.service_record_id(), body.category(), body.priority(), body.summary(),
                key, hash
            ),
            request
        );
    }

    @GetMapping("/cases/{caseId}")
    public V3ApiResponse<Map<String, Object>> detail(
        @PathVariable long caseId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(afterSaleService.detail(principal(request), shopId, caseId), request);
    }

    @PostMapping("/cases/{caseId}/actions")
    public V3ApiResponse<Map<String, Object>> action(
        @PathVariable long caseId,
        @Valid @RequestBody ActionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            caseId, body.shop_id(), body.version(), body.target_status(),
            body.note(), body.assignee_account_id()
        );
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal, key, "AFTERSALE_ACTION", hash,
            () -> result[0] = afterSaleService.action(
                principal, body.shop_id(), caseId, body.version(),
                body.target_status(), body.note(), body.assignee_account_id(), key, hash
            )
        );
        return success(
            result[0] == null
                ? afterSaleService.detail(principal, body.shop_id(), caseId)
                : result[0],
            request
        );
    }

    @PostMapping("/cases/{caseId}/refunds")
    public V3ApiResponse<Map<String, Object>> requestRefund(
        @PathVariable long caseId,
        @Valid @RequestBody RefundBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            caseId, body.shop_id(), body.payment_id(), body.amount(), body.reason()
        );
        return success(
            afterSaleService.requestRefund(
                principal(request), body.shop_id(), caseId, body.payment_id(),
                body.amount(), body.reason(), key, hash
            ),
            request
        );
    }

    @PostMapping("/cases/{caseId}/reopen")
    public V3ApiResponse<Map<String, Object>> reopen(
        @PathVariable long caseId,
        @Valid @RequestBody ReopenBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(caseId, body.shop_id(), body.version(), body.reason());
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal, key, "AFTERSALE_REOPEN", hash,
            () -> result[0] = afterSaleService.reopen(
                principal, body.shop_id(), caseId, body.version(), body.reason(), key, hash
            )
        );
        return success(
            result[0] == null
                ? afterSaleService.detail(principal, body.shop_id(), caseId)
                : result[0],
            request
        );
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        return V3RequestSupport.principal(request);
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

    private <T> V3ApiResponse<T> success(T data, HttpServletRequest request) {
        return V3ApiResponse.success(data, V3RequestSupport.requestId(request));
    }

    public record CreateBody(
        @NotNull Long shop_id,
        Long member_id,
        Long order_id,
        Long service_record_id,
        @NotBlank @Size(max = 30) String category,
        @Size(max = 20) String priority,
        @NotBlank @Size(max = 500) String summary
    ) {
    }

    public record ActionBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 30) String target_status,
        @Size(max = 500) String note,
        Long assignee_account_id
    ) {
    }

    public record RefundBody(
        @NotNull Long shop_id,
        @NotNull Long payment_id,
        @NotNull @DecimalMin("0.01") @Digits(integer = 11, fraction = 2)
        BigDecimal amount,
        @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record ReopenBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 500) String reason
    ) {
    }
}
