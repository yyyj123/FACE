package com.face.platform.v3.marketing;

import com.face.platform.idempotency.RequestHash;
import com.face.platform.marketing.MarketingConsentService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v3/me/marketing-consents")
public class V3MarketingConsentController {

    private final MarketingConsentService consentService;

    public V3MarketingConsentController(MarketingConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping
    public V3ApiResponse<Map<String, Object>> list(HttpServletRequest request) {
        return success(consentService.listMine(principal(request)), request);
    }

    @PutMapping("/{channel}")
    public V3ApiResponse<Map<String, Object>> update(
        @PathVariable String channel,
        @Valid @RequestBody UpdateBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            channel, body.status(), body.version(), MarketingConsentService.CONSENT_TEXT_VERSION,
            MarketingConsentService.CONSENT_TEXT_SHA256
        );
        return success(consentService.updateMine(
            principal, channel, body.status(), body.version(), key, hash
        ), request);
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

    public record UpdateBody(
        @NotBlank @Size(max = 20) String status,
        @NotNull Integer version
    ) {
    }
}
