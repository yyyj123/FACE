package com.face.platform.v3.commission;

import com.face.platform.commission.CommissionEntryApplicationService;
import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.security.TenantPrincipal;
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

import java.util.Map;

@RestController
@RequestMapping("/api/v3/commission")
public class V3CommissionEntryController {

    private final CommissionEntryApplicationService entryService;
    private final CommandIdempotencyService idempotencyService;

    public V3CommissionEntryController(
        CommissionEntryApplicationService entryService,
        CommandIdempotencyService idempotencyService
    ) {
        this.entryService = entryService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/entries")
    public V3ApiResponse<Map<String, Object>> entries(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(name = "staff_id", required = false) Long staffId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return success(
            entryService.list(
                principal(request),
                shopId,
                staffId,
                status,
                page,
                pageSize
            ),
            request
        );
    }

    @GetMapping("/entries/{entryId}")
    public V3ApiResponse<Map<String, Object>> entry(
        @PathVariable long entryId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            entryService.detail(principal(request), shopId, entryId),
            request
        );
    }

    @PostMapping("/entries/{entryId}/freeze")
    public V3ApiResponse<Map<String, Object>> freeze(
        @PathVariable long entryId,
        @Valid @RequestBody EntryStatusBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return changeStatus(
            entryId,
            body,
            idempotencyKey,
            request,
            true
        );
    }

    @PostMapping("/entries/{entryId}/unfreeze")
    public V3ApiResponse<Map<String, Object>> unfreeze(
        @PathVariable long entryId,
        @Valid @RequestBody EntryStatusBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return changeStatus(
            entryId,
            body,
            idempotencyKey,
            request,
            false
        );
    }

    private V3ApiResponse<Map<String, Object>> changeStatus(
        long entryId,
        EntryStatusBody body,
        String idempotencyKey,
        HttpServletRequest request,
        boolean freeze
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredIdempotencyKey(idempotencyKey);
        String requestHash = RequestHash.of(
            entryId,
            body.shop_id(),
            body.version(),
            body.reason(),
            freeze
        );
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            key,
            freeze ? "COMMISSION_ENTRY_FREEZE" : "COMMISSION_ENTRY_UNFREEZE",
            requestHash,
            () -> result[0] = freeze
                ? entryService.freeze(
                    principal,
                    body.shop_id(),
                    entryId,
                    body.version(),
                    body.reason(),
                    key,
                    requestHash
                )
                : entryService.unfreeze(
                    principal,
                    body.shop_id(),
                    entryId,
                    body.version(),
                    body.reason(),
                    key,
                    requestHash
                )
        );
        return success(
            result[0] == null
                ? entryService.detail(principal, body.shop_id(), entryId)
                : result[0],
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

    public record EntryStatusBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 500) String reason
    ) {
    }
}
