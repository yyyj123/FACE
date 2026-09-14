package com.face.platform.v3.checkout;

import com.face.platform.benefit.BenefitApplicationService;
import com.face.platform.checkout.CheckoutApplicationService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3")
public class V3CheckoutController {

    private final CheckoutApplicationService checkoutService;
    private final BenefitApplicationService benefitService;

    public V3CheckoutController(
        CheckoutApplicationService checkoutService,
        BenefitApplicationService benefitService
    ) {
        this.checkoutService = checkoutService;
        this.benefitService = benefitService;
    }

    @PostMapping("/checkout/quotes")
    public V3ApiResponse<Map<String, Object>> quote(
        @Valid @RequestBody QuoteBody body,
        HttpServletRequest request
    ) {
        return success(
            checkoutService.quote(
                principal(request),
                new CheckoutApplicationService.QuoteCommand(body.lock_token(), body.package_product_id())
            ),
            request
        );
    }

    @PostMapping("/checkouts")
    public V3ApiResponse<Map<String, Object>> create(
        @Valid @RequestBody CheckoutBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredKey(idempotencyKey);
        CheckoutApplicationService.CheckoutCommand command =
            new CheckoutApplicationService.CheckoutCommand(
                body.lock_token(), body.package_product_id(), body.selection_type(),
                body.selection_reference_id(), body.payment_method(),
                body.stored_value_card_id(), body.combo_card_id(), body.member_note()
            );
        return success(
            checkoutService.create(
                principal(request), command, key,
                CheckoutApplicationService.requestHash(command)
            ),
            request
        );
    }

    @GetMapping("/checkouts/{orderId}")
    public V3ApiResponse<Map<String, Object>> detail(
        @PathVariable long orderId,
        HttpServletRequest request
    ) {
        return success(checkoutService.detail(principal(request), orderId), request);
    }

    @GetMapping("/client/card-products")
    public V3ApiResponse<List<Map<String, Object>>> cardProducts(HttpServletRequest request) {
        return success(checkoutService.purchasableCards(principal(request)), request);
    }

    @GetMapping("/client/benefits")
    public V3ApiResponse<Map<String, Object>> myBenefits(HttpServletRequest request) {
        return success(benefitService.myBenefits(principal(request)), request);
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

    public record QuoteBody(
        @Size(max = 36) String lock_token,
        Long package_product_id
    ) {
    }

    public record CheckoutBody(
        @Size(max = 36) String lock_token,
        Long package_product_id,
        @Size(max = 24) String selection_type,
        Long selection_reference_id,
        @Size(max = 30) String payment_method,
        Long stored_value_card_id,
        Long combo_card_id,
        @Size(max = 1000) String member_note
    ) {
    }
}
