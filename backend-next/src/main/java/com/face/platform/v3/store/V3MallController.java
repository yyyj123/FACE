package com.face.platform.v3.store;

import com.face.platform.security.TenantPrincipal;
import com.face.platform.store.MallApplicationService;
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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
public class V3MallController {

    private final MallApplicationService service;

    public V3MallController(MallApplicationService service) {
        this.service = service;
    }

    @GetMapping("/client/mall")
    public V3ApiResponse<Map<String, Object>> catalog(HttpServletRequest request) {
        return success(service.catalog(principal(request)), request);
    }

    @GetMapping("/client/mall/cart")
    public V3ApiResponse<Map<String, Object>> cart(HttpServletRequest request) {
        return success(service.cart(principal(request)), request);
    }

    @PostMapping("/client/mall/cart")
    public V3ApiResponse<Map<String, Object>> putCart(
        @Valid @RequestBody CartBody body,
        HttpServletRequest request
    ) {
        return success(service.putCart(
            principal(request),
            new MallApplicationService.CartCommand(
                body.sku_id(), body.purchase_mode(), body.quantity(),
                body.delivery_mode(), body.pickup_shop_id()
            )
        ), request);
    }

    @DeleteMapping("/client/mall/cart/{itemId}")
    public V3ApiResponse<Map<String, Object>> removeCartItem(
        @PathVariable long itemId,
        HttpServletRequest request
    ) {
        return success(service.removeCartItem(principal(request), itemId), request);
    }

    @PutMapping("/client/mall/cart/{itemId}")
    public V3ApiResponse<Map<String, Object>> updateCartItemQuantity(
        @PathVariable long itemId,
        @Valid @RequestBody CartQuantityBody body,
        HttpServletRequest request
    ) {
        return success(
            service.updateCartItemQuantity(principal(request), itemId, body.quantity()),
            request
        );
    }

    @PostMapping("/client/mall/checkouts")
    public V3ApiResponse<Map<String, Object>> checkout(
        @Valid @RequestBody CheckoutBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredKey(idempotencyKey);
        MallApplicationService.CheckoutCommand command = new MallApplicationService.CheckoutCommand(
            body.payment_method(), body.mall_coupon_id(), body.address()
        );
        return success(service.checkout(
            principal(request), command, key, MallApplicationService.requestHash(command)
        ), request);
    }

    @GetMapping("/client/mall/orders/{orderId}")
    public V3ApiResponse<Map<String, Object>> order(
        @PathVariable long orderId,
        HttpServletRequest request
    ) {
        return success(service.detail(principal(request), orderId), request);
    }

    @PostMapping("/client/mall/orders/{orderId}/cancel")
    public V3ApiResponse<Map<String, Object>> cancel(
        @PathVariable long orderId,
        HttpServletRequest request
    ) {
        return success(service.cancelPending(principal(request), orderId), request);
    }

    @PostMapping("/client/mall/packages/{packageId}/receive")
    public V3ApiResponse<Map<String, Object>> receive(
        @PathVariable long packageId,
        HttpServletRequest request
    ) {
        return success(service.receivePackage(principal(request), packageId), request);
    }

    @GetMapping("/admin/mall")
    public V3ApiResponse<Map<String, Object>> administration(
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(service.administration(principal(request), shopId), request);
    }

    @PostMapping("/admin/mall/products")
    public V3ApiResponse<Map<String, Object>> createProduct(
        @Valid @RequestBody ProductBody body,
        HttpServletRequest request
    ) {
        return success(service.createProduct(
            principal(request),
            new MallApplicationService.ProductCommand(
                body.shop_id(), body.category_code(), body.category_name(),
                body.product_code(), body.product_type(), body.name(), body.brand_name(),
                body.description(), body.cover_url(), body.care_service_id(), body.delivery_mode(),
                body.freight_template_code(), body.separate_shipping(), body.after_sale_policy(),
                body.sku_code(), body.spec(), body.cash_price(), body.points_price(),
                body.combo_cash_price(), body.combo_points_price(), body.cash_enabled(),
                body.points_enabled(), body.combo_enabled(), body.purchase_limit(),
                body.warning_threshold()
            )
        ), request);
    }

    @PutMapping("/admin/mall/products/{productId}/image")
    public V3ApiResponse<Map<String, Object>> updateProductImage(
        @PathVariable long productId,
        @Valid @RequestBody ProductImageBody body,
        HttpServletRequest request
    ) {
        return success(service.updateProductImage(
            principal(request), body.shop_id(), productId, body.cover_url()
        ), request);
    }

    @PostMapping("/admin/mall/inventory/adjustments")
    public V3ApiResponse<Map<String, Object>> adjustInventory(
        @Valid @RequestBody InventoryBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(service.adjustInventory(
            principal(request),
            new MallApplicationService.InventoryCommand(
                body.shop_id(), body.sku_id(), body.delta(), body.reason()
            ),
            requiredKey(idempotencyKey)
        ), request);
    }

    @PostMapping("/admin/mall/packages")
    public V3ApiResponse<Map<String, Object>> createPackage(
        @Valid @RequestBody PackageBody body,
        HttpServletRequest request
    ) {
        return success(service.createPackage(
            principal(request),
            new MallApplicationService.PackageCommand(
                body.shop_id(), body.sub_order_ids(),
                body.logistics_company(), body.tracking_no()
            )
        ), request);
    }

    @PostMapping("/admin/mall/coupon-rules")
    public V3ApiResponse<Map<String, Object>> createCouponRule(
        @Valid @RequestBody CouponRuleBody body,
        HttpServletRequest request
    ) {
        return success(service.createCouponRule(
            principal(request),
            new MallApplicationService.CouponRuleCommand(
                body.shop_id(), body.coupon_code(), body.name(), body.coupon_type(),
                body.fixed_amount(), body.minimum_cash_amount(), body.product_ids(),
                body.valid_days()
            )
        ), request);
    }

    @PostMapping("/admin/mall/coupons/issue")
    public V3ApiResponse<Map<String, Object>> issueCoupon(
        @Valid @RequestBody CouponIssueBody body,
        HttpServletRequest request
    ) {
        return success(service.issueCoupon(
            principal(request),
            new MallApplicationService.CouponIssueCommand(
                body.shop_id(), body.coupon_rule_id(), body.member_id()
            )
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

    public record CartBody(
        @Positive long sku_id,
        @NotBlank @Size(max = 20) String purchase_mode,
        @Positive int quantity,
        @NotBlank @Size(max = 20) String delivery_mode,
        Long pickup_shop_id
    ) {
    }

    public record CartQuantityBody(@Positive int quantity) {
    }

    public record CheckoutBody(
        @NotBlank @Size(max = 30) String payment_method,
        Long mall_coupon_id,
        Map<String, Object> address
    ) {
    }

    public record ProductBody(
        @Positive long shop_id,
        @NotBlank @Size(max = 48) String category_code,
        @NotBlank @Size(max = 100) String category_name,
        @NotBlank @Size(max = 48) String product_code,
        @NotBlank @Size(max = 24) String product_type,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 100) String brand_name,
        @Size(max = 5000) String description,
        @Size(max = 500) String cover_url,
        Long care_service_id,
        @NotBlank @Size(max = 24) String delivery_mode,
        @Size(max = 48) String freight_template_code,
        boolean separate_shipping,
        @Size(max = 500) String after_sale_policy,
        @NotBlank @Size(max = 64) String sku_code,
        Map<String, Object> spec,
        BigDecimal cash_price,
        Long points_price,
        BigDecimal combo_cash_price,
        Long combo_points_price,
        boolean cash_enabled,
        boolean points_enabled,
        boolean combo_enabled,
        Integer purchase_limit,
        @Min(0) int warning_threshold
    ) {
    }

    public record ProductImageBody(
        @Positive long shop_id,
        @Size(max = 500) String cover_url
    ) {
    }

    public record InventoryBody(
        @Positive long shop_id,
        @Positive long sku_id,
        int delta,
        @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record PackageBody(
        @Positive long shop_id,
        @NotEmpty List<@Positive Long> sub_order_ids,
        @Size(max = 100) String logistics_company,
        @Size(max = 100) String tracking_no
    ) {
    }

    public record CouponRuleBody(
        @Positive long shop_id,
        @NotBlank @Size(max = 48) String coupon_code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 20) String coupon_type,
        @NotNull @Positive BigDecimal fixed_amount,
        @NotNull @Min(0) BigDecimal minimum_cash_amount,
        List<@Positive Long> product_ids,
        @Min(1) @Max(3650) int valid_days
    ) {
    }

    public record CouponIssueBody(
        @Positive long shop_id,
        @Positive long coupon_rule_id,
        @Positive long member_id
    ) {
    }
}
