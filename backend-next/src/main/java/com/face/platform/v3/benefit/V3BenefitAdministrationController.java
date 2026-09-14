package com.face.platform.v3.benefit;

import com.face.platform.benefit.BenefitApplicationService;
import com.face.platform.payment.PaymentAdapterRegistry;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import com.face.platform.v3.auth.SessionTokenCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/benefits")
public class V3BenefitAdministrationController {

    private final BenefitApplicationService benefitService;
    private final TenantAccessService accessService;
    private final PaymentAdapterRegistry adapterRegistry;

    public V3BenefitAdministrationController(
        BenefitApplicationService benefitService,
        TenantAccessService accessService,
        PaymentAdapterRegistry adapterRegistry
    ) {
        this.benefitService = benefitService;
        this.accessService = accessService;
        this.adapterRegistry = adapterRegistry;
    }

    @GetMapping("/payment-channels")
    public V3ApiResponse<List<Map<String, Object>>> paymentChannels(
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        accessService.requireShopPermission(principal, shopId, "payment_config:view");
        return success(
            List.of("DEMO_MOCK", "WECHAT", "ALIPAY", "AGGREGATOR").stream()
                .map(code -> Map.<String, Object>of(
                    "code", code,
                    "configured", adapterRegistry.configured(code),
                    "maskedConfig", adapterRegistry.configured(code) ? "••••••••" : "未配置",
                    "message", adapterRegistry.configured(code) ? "已配置" : "支付渠道暂未开通"
                ))
                .toList(),
            request
        );
    }

    @GetMapping
    public V3ApiResponse<Map<String, Object>> administration(
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(benefitService.administration(principal(request), shopId), request);
    }

    @PostMapping("/card-products")
    public V3ApiResponse<Map<String, Object>> createCardProduct(
        @Valid @RequestBody CardProductBody body,
        HttpServletRequest request
    ) {
        List<BenefitApplicationService.CardProductItem> items = body.items() == null
            ? List.of()
            : body.items().stream()
                .map(item -> new BenefitApplicationService.CardProductItem(
                    item.service_id(), item.quantity()
                ))
                .toList();
        return success(
            benefitService.createCardProduct(
                principal(request),
                new BenefitApplicationService.CardProductCommand(
                    body.shop_id(), body.package_code(), body.name(), body.description(),
                    body.card_type(), body.sale_price(), body.principal_amount(),
                    body.gift_amount(), body.discount_percent(), body.minimum_spend(),
                    body.maximum_savings(), body.usage_limit(), body.validity_days(),
                    body.scope_json(), items
                )
            ),
            request
        );
    }

    @PostMapping("/cards/issue")
    public V3ApiResponse<Map<String, Object>> issueCard(
        @Valid @RequestBody CardIssueBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredKey(idempotencyKey);
        String hash = SessionTokenCodec.sha256(
            body.shop_id() + "|" + body.member_id() + "|" + body.package_product_id()
                + "|" + body.source_type() + "|" + body.source_reference()
        );
        return success(
            benefitService.issueManualCard(
                principal(request),
                new BenefitApplicationService.CardIssueCommand(
                    body.shop_id(), body.member_id(), body.package_product_id(),
                    body.source_type(), body.source_reference()
                ),
                key,
                hash
            ),
            request
        );
    }

    @PostMapping("/coupon-templates")
    public V3ApiResponse<Map<String, Object>> createCouponTemplate(
        @Valid @RequestBody CouponTemplateBody body,
        HttpServletRequest request
    ) {
        return success(
            benefitService.createCouponTemplate(
                principal(request),
                new BenefitApplicationService.CouponTemplateCommand(
                    body.shop_id(), body.template_code(), body.name(), body.coupon_type(),
                    body.threshold_amount(), body.benefit_value(), body.service_id(),
                    body.validity_days(), body.return_on_full_refund()
                )
            ),
            request
        );
    }

    @PostMapping("/coupons/issue")
    public V3ApiResponse<Map<String, Object>> issueCoupon(
        @Valid @RequestBody CouponIssueBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredKey(idempotencyKey);
        String hash = SessionTokenCodec.sha256(
            body.shop_id() + "|" + body.member_id() + "|" + body.template_id()
                + "|" + body.source_type() + "|" + body.source_reference()
        );
        return success(
            benefitService.issueCoupon(
                principal(request),
                new BenefitApplicationService.CouponIssueCommand(
                    body.shop_id(), body.member_id(), body.template_id(),
                    body.source_type(), body.source_reference()
                ),
                key,
                hash
            ),
            request
        );
    }

    @PostMapping("/orders/{orderId}/consume")
    public V3ApiResponse<Map<String, Object>> consume(
        @PathVariable long orderId,
        @Valid @RequestBody OrderBenefitBody body,
        HttpServletRequest request
    ) {
        int count = benefitService.consumeReservedBenefits(
            principal(request), body.shop_id(), orderId
        );
        return success(Map.of("orderId", orderId, "consumedCount", count), request);
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

    public record CardProductBody(
        @NotNull Long shop_id,
        @NotBlank @Size(max = 48) String package_code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 24) String card_type,
        @NotNull @DecimalMin("0.00") BigDecimal sale_price,
        @DecimalMin("0.00") BigDecimal principal_amount,
        @DecimalMin("0.00") BigDecimal gift_amount,
        @DecimalMin("0.01") BigDecimal discount_percent,
        @DecimalMin("0.00") BigDecimal minimum_spend,
        @DecimalMin("0.00") BigDecimal maximum_savings,
        Integer usage_limit,
        @Min(1) int validity_days,
        String scope_json,
        List<@Valid CardProductItemBody> items
    ) {
    }

    public record CardProductItemBody(
        @Min(1) long service_id,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity
    ) {
    }

    public record CardIssueBody(
        @NotNull Long shop_id,
        @NotNull Long member_id,
        @NotNull Long package_product_id,
        @NotBlank String source_type,
        @Size(max = 100) String source_reference
    ) {
    }

    public record CouponTemplateBody(
        @NotNull Long shop_id,
        @NotBlank @Size(max = 48) String template_code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank String coupon_type,
        @DecimalMin("0.00") BigDecimal threshold_amount,
        @DecimalMin("0.00") BigDecimal benefit_value,
        Long service_id,
        @Min(1) int validity_days,
        boolean return_on_full_refund
    ) {
    }

    public record CouponIssueBody(
        @NotNull Long shop_id,
        @NotNull Long member_id,
        @NotNull Long template_id,
        @NotBlank String source_type,
        @Size(max = 100) String source_reference
    ) {
    }

    public record OrderBenefitBody(@NotNull Long shop_id) {
    }
}
