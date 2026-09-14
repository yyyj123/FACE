package com.face.platform.v3.notification;

import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.notification.NotificationApplicationService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v3/notifications")
public class V3NotificationController {

    private final NotificationApplicationService notificationService;
    private final CommandIdempotencyService idempotencyService;

    public V3NotificationController(
        NotificationApplicationService notificationService,
        CommandIdempotencyService idempotencyService
    ) {
        this.notificationService = notificationService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public V3ApiResponse<Map<String, Object>> list(
        @RequestParam(defaultValue = "ALL") String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return success(
            notificationService.list(principal(request), status, page, pageSize),
            request
        );
    }

    @PostMapping("/{notificationId}/read")
    public V3ApiResponse<Map<String, Object>> markRead(
        @PathVariable long notificationId,
        @Valid @RequestBody ReadBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(notificationId, body.version());
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            key,
            "NOTIFICATION_READ",
            hash,
            () -> result[0] = notificationService.markRead(
                principal, notificationId, body.version()
            )
        );
        return success(
            result[0] == null ? notificationService.summary(principal) : result[0],
            request
        );
    }

    @PostMapping("/read-all")
    public V3ApiResponse<Map<String, Object>> markAllRead(
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(principal.accountId(), "READ_ALL");
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            key,
            "NOTIFICATION_READ_ALL",
            hash,
            () -> result[0] = notificationService.markAllRead(principal)
        );
        return success(
            result[0] == null ? notificationService.summary(principal) : result[0],
            request
        );
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

    public record ReadBody(@NotNull Integer version) {
    }
}
