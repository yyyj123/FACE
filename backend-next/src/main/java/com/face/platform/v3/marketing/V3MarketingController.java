package com.face.platform.v3.marketing;

import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.marketing.MarketingCampaignApplicationService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/marketing/campaigns")
public class V3MarketingController {

    private final MarketingCampaignApplicationService campaignService;
    private final CommandIdempotencyService idempotencyService;

    public V3MarketingController(
        MarketingCampaignApplicationService campaignService,
        CommandIdempotencyService idempotencyService
    ) {
        this.campaignService = campaignService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public V3ApiResponse<Map<String, Object>> list(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return success(campaignService.list(
            principal(request), shopId, status, page, pageSize
        ), request);
    }

    @PostMapping
    public V3ApiResponse<Map<String, Object>> create(
        @Valid @RequestBody CreateBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            body.shop_id(), body.title(), body.safe_summary(), body.channel(),
            body.action_path(), body.scheduled_at()
        );
        return success(campaignService.create(
            principal(request), body.shop_id(), body.request(), key, hash
        ), request);
    }

    @GetMapping("/{campaignId}")
    public V3ApiResponse<Map<String, Object>> detail(
        @PathVariable long campaignId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(campaignService.detail(
            principal(request), shopId, campaignId
        ), request);
    }

    @PatchMapping("/{campaignId}")
    public V3ApiResponse<Map<String, Object>> update(
        @PathVariable long campaignId,
        @Valid @RequestBody UpdateBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            campaignId, body.shop_id(), body.version(), body.title(), body.safe_summary(),
            body.channel(), body.action_path(), body.scheduled_at()
        );
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(principal, key, "MARKETING_CAMPAIGN_UPDATE", hash,
            () -> result[0] = campaignService.update(
                principal, body.shop_id(), campaignId, body.version(), body.request()
            ));
        return success(result[0] == null
            ? campaignService.detail(principal, body.shop_id(), campaignId) : result[0], request);
    }

    @PostMapping("/{campaignId}/submit")
    public V3ApiResponse<Map<String, Object>> submit(
        @PathVariable long campaignId,
        @Valid @RequestBody VersionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(campaignId, body.shop_id(), body.version(), "SUBMIT");
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(principal, key, "MARKETING_CAMPAIGN_SUBMIT", hash,
            () -> result[0] = campaignService.submit(
                principal, body.shop_id(), campaignId, body.version(), key, hash
            ));
        return success(result[0] == null
            ? campaignService.detail(principal, body.shop_id(), campaignId) : result[0], request);
    }

    @PostMapping("/{campaignId}/decisions")
    public V3ApiResponse<Map<String, Object>> decide(
        @PathVariable long campaignId,
        @Valid @RequestBody DecisionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            campaignId, body.shop_id(), body.version(), body.action(), body.reason()
        );
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(principal, key, "MARKETING_CAMPAIGN_DECISION", hash,
            () -> result[0] = campaignService.decide(
                principal, body.shop_id(), campaignId, body.version(),
                body.action(), body.reason(), key, hash
            ));
        return success(result[0] == null
            ? campaignService.detail(principal, body.shop_id(), campaignId) : result[0], request);
    }

    @PostMapping("/{campaignId}/execute")
    public V3ApiResponse<Map<String, Object>> execute(
        @PathVariable long campaignId,
        @Valid @RequestBody VersionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(campaignId, body.shop_id(), body.version(), "EXECUTE");
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(principal, key, "MARKETING_CAMPAIGN_EXECUTE", hash,
            () -> result[0] = campaignService.execute(
                principal, body.shop_id(), campaignId, body.version(), key, hash
            ));
        return success(result[0] == null
            ? campaignService.detail(principal, body.shop_id(), campaignId) : result[0], request);
    }

    @PostMapping("/{campaignId}/cancel")
    public V3ApiResponse<Map<String, Object>> cancel(
        @PathVariable long campaignId,
        @Valid @RequestBody CancelBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredKey(idempotencyKey);
        String hash = RequestHash.of(
            campaignId, body.shop_id(), body.version(), body.reason(), "CANCEL"
        );
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(principal, key, "MARKETING_CAMPAIGN_CANCEL", hash,
            () -> result[0] = campaignService.cancel(
                principal, body.shop_id(), campaignId, body.version(), body.reason(), key, hash
            ));
        return success(result[0] == null
            ? campaignService.detail(principal, body.shop_id(), campaignId) : result[0], request);
    }

    @GetMapping("/{campaignId}/audience")
    public V3ApiResponse<Map<String, Object>> audience(
        @PathVariable long campaignId,
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return success(campaignService.audience(
            principal(request), shopId, campaignId, page, pageSize
        ), request);
    }

    @GetMapping("/{campaignId}/deliveries")
    public V3ApiResponse<Map<String, Object>> deliveries(
        @PathVariable long campaignId,
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return success(campaignService.deliveries(
            principal(request), shopId, campaignId, page, pageSize
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

    public record CreateBody(
        @NotNull Long shop_id,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 500) String safe_summary,
        @NotBlank @Size(max = 20) String channel,
        @Size(max = 255) String action_path,
        LocalDateTime scheduled_at
    ) {
        MarketingCampaignApplicationService.CreateRequest request() {
            return new MarketingCampaignApplicationService.CreateRequest(
                title, safe_summary, channel, action_path, scheduled_at
            );
        }
    }

    public record UpdateBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 500) String safe_summary,
        @NotBlank @Size(max = 20) String channel,
        @Size(max = 255) String action_path,
        LocalDateTime scheduled_at
    ) {
        MarketingCampaignApplicationService.CreateRequest request() {
            return new MarketingCampaignApplicationService.CreateRequest(
                title, safe_summary, channel, action_path, scheduled_at
            );
        }
    }

    public record VersionBody(@NotNull Long shop_id, @NotNull Integer version) {
    }

    public record DecisionBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 20) String action,
        @Size(max = 500) String reason
    ) {
    }

    public record CancelBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 500) String reason
    ) {
    }
}
