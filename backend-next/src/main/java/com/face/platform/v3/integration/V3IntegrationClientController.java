package com.face.platform.v3.integration;

import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.integration.IntegrationClientApplicationService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/integrations/clients")
public class V3IntegrationClientController {

    private final IntegrationClientApplicationService service;
    private final CommandIdempotencyService idempotency;

    public V3IntegrationClientController(IntegrationClientApplicationService service, CommandIdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @GetMapping
    public V3ApiResponse<List<Map<String, Object>>> list(
        @RequestParam(name = "shop_id") long shopId, HttpServletRequest request
    ) {
        return ok(service.list(principal(request), shopId), request);
    }

    @PostMapping
    public V3ApiResponse<Map<String, Object>> create(
        @Valid @RequestBody CreateBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String key,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String safeKey = key(key);
        String hash = RequestHash.of(body.shop_id(), body.client_code(), body.client_name(), body.safe_description(), body.scopes(), body.rate_limit_per_minute());
        final Map<String, Object>[] result = new Map[1];
        idempotency.run(principal, safeKey, "INTEGRATION_CLIENT_CREATE", hash,
            () -> result[0] = service.create(principal, body.shop_id(), body.client_code(), body.client_name(),
                body.safe_description(), body.scopes(), body.rate_limit_per_minute(), safeKey, hash));
        return ok(result[0] == null ? Map.of("replayed", true, "secretShownOnce", false) : result[0], request);
    }

    @PostMapping("/{clientId}/rotate-secret")
    public V3ApiResponse<Map<String, Object>> rotate(
        @PathVariable long clientId,
        @Valid @RequestBody CommandBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String key,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String safeKey = key(key);
        String hash = RequestHash.of(clientId, body.shop_id(), body.version(), body.reason(), "ROTATE");
        final Map<String, Object>[] result = new Map[1];
        idempotency.run(principal, safeKey, "INTEGRATION_CLIENT_ROTATE", hash,
            () -> result[0] = service.rotate(principal, body.shop_id(), clientId, body.version(), body.reason(), safeKey, hash));
        return ok(result[0] == null ? Map.of("replayed", true, "secretShownOnce", false) : result[0], request);
    }

    @PostMapping("/{clientId}/revoke")
    public V3ApiResponse<Map<String, Object>> revoke(
        @PathVariable long clientId,
        @Valid @RequestBody CommandBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String key,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String safeKey = key(key);
        String hash = RequestHash.of(clientId, body.shop_id(), body.version(), body.reason(), "REVOKE");
        final Map<String, Object>[] result = new Map[1];
        idempotency.run(principal, safeKey, "INTEGRATION_CLIENT_REVOKE", hash,
            () -> result[0] = service.revoke(principal, body.shop_id(), clientId, body.version(), body.reason(), safeKey, hash));
        return ok(result[0] == null ? Map.of("replayed", true) : result[0], request);
    }

    private TenantPrincipal principal(HttpServletRequest request) { return V3RequestSupport.principal(request); }
    private <T> V3ApiResponse<T> ok(T data, HttpServletRequest request) { return V3ApiResponse.success(data, V3RequestSupport.requestId(request)); }
    private String key(String value) {
        if (value == null || value.isBlank() || value.length() > 80 || !value.matches("[A-Za-z0-9._:-]+"))
            throw new V3ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "关键写操作必须提供有效的 Idempotency-Key");
        return value;
    }

    public record CreateBody(
        @NotNull Long shop_id,
        @NotBlank @Size(max = 40) String client_code,
        @NotBlank @Size(max = 120) String client_name,
        @Size(max = 500) String safe_description,
        @NotEmpty List<@NotBlank String> scopes,
        @Min(1) @Max(60) int rate_limit_per_minute
    ) { }
    public record CommandBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 500) String reason
    ) { }
}
